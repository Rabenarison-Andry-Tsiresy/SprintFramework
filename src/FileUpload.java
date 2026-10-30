package src;

import java.util.LinkedHashMap;
import java.util.Map;

// Sprint 8 : upload de fichier (multipart/form-data).
// Objet rempli par ParamBinder a partir de request.getPart("filemap")
// puis injecte directement dans la methode du controleur (instance, pas byte[]).
public class FileUpload {

    private String fieldName;
    private String fileName;
    private String extension;
    private String contentType;
    private byte[] content;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    public FileUpload() {
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public long getSize() {
        return content == null ? 0L : content.length;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? metadata : new LinkedHashMap<>();
    }

    // Metadonnees utiles au stockage (nom, extension, type MIME, taille...).
    public Map<String, Object> getFilemap() {
        return metadata;
    }

    @Override
    public String toString() {
        return "FileUpload{fieldName='" + fieldName + "', fileName='" + fileName
                + "', extension='" + extension + "', contentType='" + contentType
                + "', size=" + getSize() + "}";
    }
}
