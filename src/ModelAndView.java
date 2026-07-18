package src;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {

    private String viewName;
    private Map<String, Object> data;

    public ModelAndView(String viewName) {
        this.viewName = viewName;
        this.data = new HashMap<>();
    }

    public String getViewName() {
        return viewName;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public ModelAndView addAttribute(String key, Object value) {
        this.data.put(key, value);
        return this;
    }
}
