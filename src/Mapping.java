package src;

import java.util.Objects;

public class Mapping {

    private String url;
    private String httpMethod;

    public Mapping(String url, String httpMethod) {
        this.url = url;
        this.httpMethod = httpMethod.toUpperCase();
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
        if (!(obj instanceof Mapping)) return false;

        Mapping other = (Mapping) obj;

        return url.equals(other.url)
                && httpMethod.equals(other.httpMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, httpMethod);
    }
}