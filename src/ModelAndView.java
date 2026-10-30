package src;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {

    private String viewName;
    private Map<String, Object> data;

    public ModelAndView() {
        this("");
    }

    public ModelAndView(String viewName) {
        this.viewName = viewName != null ? viewName : "";
        this.data = new HashMap<>();
    }

    public ModelAndView(String viewName, Map<String, Object> data) {
        this.viewName = viewName != null ? viewName : "";
        this.data = data != null ? data : new HashMap<>();
    }

    public String getViewName() {
        return viewName;
    }

    public void setViewName(String viewName) {
        this.viewName = viewName != null ? viewName : "";
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data != null ? data : new HashMap<>();
    }

    public ModelAndView addAttribute(String key, Object value) {
        if (this.data == null) {
            this.data = new HashMap<>();
        }
        this.data.put(key, value);
        return this;
    }

    // Alias Spring MVC standard
    public ModelAndView addObject(String key, Object value) {
        return addAttribute(key, value);
    }
}
