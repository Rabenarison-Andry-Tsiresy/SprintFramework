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

     public HttpSession getHttpSession() {
          return httpSession;
     }

     public void set(String key, Object value) {
          String k = requireKey(key);
          data().put(k, value);
          // Synchronise avec HttpSession pour accès direct en JSP : ${sessionScope.cle} ou ${cle}
          if (value != null) {
              httpSession.setAttribute(k, value);
          } else {
              httpSession.removeAttribute(k);
          }
     }

     public <T> T get(String key) {
          String k = requireKey(key);
          Object val = data().get(k);
          if (val == null) {
              val = httpSession.getAttribute(k);
          }
          return value(val);
     }

     public <T> T getOrDefault(String key, T defaultValue) {
          T value = get(key);
          return value != null ? value : defaultValue;
     }

     public boolean contains(String key) {
          String k = requireKey(key);
          return data().containsKey(k) || httpSession.getAttribute(k) != null;
     }

     public Object remove(String key) {
          String k = requireKey(key);
          httpSession.removeAttribute(k);
          return data().remove(k);
     }

     public void clear() {
          for (String k : data().keySet()) {
              httpSession.removeAttribute(k);
          }
          data().clear();
     }

     public Map<String, Object> getAll() {
          return new HashMap<>(data());
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
          return key.trim();
     }

     @SuppressWarnings("unchecked")
     private static <T> T value(Object value) {
          return (T) value;
     }
}