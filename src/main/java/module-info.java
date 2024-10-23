module com.example.pacman3d {
    requires javafx.controls;
    requires javafx.fxml;
    requires jimObjModelImporterJFX;
    requires javafx.media;

    opens com.example.pacman3d to javafx.fxml;
    exports com.example.pacman3d;
}
