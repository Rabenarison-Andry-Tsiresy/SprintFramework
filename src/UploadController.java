package tsiresy.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import jakarta.servlet.http.Part;
import src.annotation.Controller;
import src.annotation.Param;
import src.annotation.PostMapping;

@Controller
public class UploadController {

    @PostMapping("/upload")
    public String upload(@Param(name = "file") Part file) throws IOException {
        String originalName = file.getSubmittedFileName();

        if (originalName == null || originalName.isBlank()) {
            return "Nom de fichier invalide";
        }

        String safeName = Paths.get(originalName)
                .getFileName()
                .toString();

        Path uploadDirectory = Paths.get("uploads");
        Files.createDirectories(uploadDirectory);

        Path destination = uploadDirectory.resolve(safeName);

        Files.copy(
            file.getInputStream(),
            destination,
            StandardCopyOption.REPLACE_EXISTING
        );

        return "Fichier uploadé : " + safeName;
    }
}