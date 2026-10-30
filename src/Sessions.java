package src;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;


public class Sessions {
     private static final String DATA_ATTRIBUTE = Sessions.class.getName() + ".data";

     private final HttpSession httpSession;

     public Sessions(HttpServletRequest request) {
          this(request.getSession());
     }

     public Sessions(HttpSession httpSession) {
          if (httpSession == null) {
               throw new IllegalArgumentException("La session HTTP ne peut pas etre null.");
          }
          this.httpSession = httpSession;
     }

     public void set(String key, Object value) {
          data().put(requireKey(key), value);
     }

     public <T> T get(String key) {
          return value(data().get(requireKey(key)));
     }

     public <T> T getOrDefault(String key, T defaultValue) {
          T value = get(key);
          return value != null ? value : defaultValue;
     }

     public boolean contains(String key) {
          return data().containsKey(requireKey(key));
     }

     public Object remove(String key) {
          return data().remove(requireKey(key));
     }

     public void clear() {
          data().clear();
     }

     public void invalidate() {
          httpSession.invalidate();
     }

     @SuppressWarnings("unchecked")
     private Map<String, Object> data() {
          Object value = httpSession.getAttribute(DATA_ATTRIBUTE);
          if (value == null) {
               Map<String, Object> newData = new HashMap<>();
               httpSession.setAttribute(DATA_ATTRIBUTE, newData);
               return newData;
          }
          return (Map<String, Object>) value;
     }

     private static String requireKey(String key) {
          if (key == null || key.isBlank()) {
               throw new IllegalArgumentException("La cle de session ne peut pas etre vide.");
          }
          return key;
     }

     @SuppressWarnings("unchecked")
     private static <T> T value(Object value) {
          return (T) value;
     }
}