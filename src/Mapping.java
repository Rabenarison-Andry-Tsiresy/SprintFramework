package src;

import java.util.Objects;

public class Mapping {

    private String url;
    private String httpMethod;

    public Mapping(String url, String httpMethod) {
        this.url = normalizeUrl(url);
        this.httpMethod = (httpMethod == null || httpMethod.isBlank()) ? "GET" : httpMethod.trim().toUpperCase();
    }

    private static String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return "/";
        }
        String trimmed = url.trim();
        if (!trimmed.startsWith("/")) {
            trimmed = "/" + trimmed;
        }
        return trimmed;
    }

    public String getUrl() {
        return url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Mapping other)) return false;
        return Objects.equals(url, other.url)
                && Objects.equals(httpMethod, other.httpMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, httpMethod);
    }
}