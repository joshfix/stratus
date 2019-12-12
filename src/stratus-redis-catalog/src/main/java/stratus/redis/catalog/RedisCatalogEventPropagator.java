package stratus.redis.catalog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.geoserver.catalog.Catalog;
import org.geoserver.catalog.CatalogException;
import org.geoserver.catalog.CatalogInfo;
import org.geoserver.catalog.event.*;
import org.geoserver.catalog.event.impl.CatalogEventImpl;
import org.geoserver.catalog.event.impl.CatalogPostModifyEventImpl;
import org.geoserver.platform.resource.ResourceNotification;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;
import stratus.redis.catalog.config.StratusCatalogConfigProps;
import stratus.redis.store.RedisResourceNotification;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author joshfix
 * Created on 12/11/19
 */
@Slf4j
@Component
@AllArgsConstructor
public class RedisCatalogEventPropagator implements CatalogListener, MessageListener {

    private Collection<CatalogListener> catalogListeners;
    private ObjectMapper mapper = new ObjectMapper();

    private final Catalog catalog;
    private final StratusCatalogConfigProps configProps;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisMessageListenerContainer messageContainer;

    public static final UUID ID = UUID.randomUUID();
    public static final String POST_MODIFY_CHANNEL = "catalogPostModify";

    @PostConstruct
    public void init() {
        catalog.addListener(this);
        catalogListeners = catalog.getListeners();
        messageContainer.addMessageListener(this, new ChannelTopic(POST_MODIFY_CHANNEL));
    }

    @Override
    public void handleAddEvent(CatalogAddEvent event) throws CatalogException {}

    @Override
    public void handleRemoveEvent(CatalogRemoveEvent event) throws CatalogException {}

    @Override
    public void handleModifyEvent(CatalogModifyEvent event) throws CatalogException {}

    @Override
    public void handlePostModifyEvent(CatalogPostModifyEvent event) throws CatalogException {
        try {
            CatalogInfo source = event.getSource();
            ((CatalogEventImpl)event).setSource(null);
            String message = mapper.writeValueAsString(new RedisCatalogEventNotification((CatalogPostModifyEventImpl)event, ID));
            log.error("post modify message; " + message);
            redisTemplate.convertAndSend(POST_MODIFY_CHANNEL, message);
            ((CatalogEventImpl)event).setSource(source);
        } catch (JsonProcessingException e) {
            throw new CatalogException(e);
        }
    }

    @Override
    public void reloaded() {}

    @Override
    public void onMessage(Message message, byte[] bytes) {

        try {
            RedisCatalogEventNotification notification =
                    mapper.readValue(message.getBody(), RedisCatalogEventNotification.class);

            // if this instance published the message, don't propagate it.
            if (notification.getId().equals(ID)) {
                log.error("This instance published the notification.  Disregarding.");
                return;
            }

            for (CatalogListener listener : catalogListeners) {
                if (configProps.getNotifications().getListeners().contains(listener.getClass().getSimpleName())) {
                    log.error("propagating message to " + listener.getClass().getSimpleName());
                    ((CatalogEventImpl)notification.getEvent()).setSource(catalog);
                    listener.handlePostModifyEvent(notification.getEvent());
                }
            }
        } catch (Exception e) {
            log.error("Error handling catalog post modify event notification.", e);
        }
    }

    @Data
    public static class RedisCatalogEventNotification {

        private CatalogPostModifyEventImpl event;
        private UUID id;

        @JsonCreator
        public RedisCatalogEventNotification(
                @JsonProperty("event") CatalogPostModifyEventImpl event,
                @JsonProperty("id") UUID id
        ) {
            this.event = event;
            this.id = id;
        }


    }
}
