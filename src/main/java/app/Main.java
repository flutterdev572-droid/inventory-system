package app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Paths;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        // مسار ملف الإعداد
        String configPath = Paths.get(System.getProperty("user.home"), "warehouse_db_config.properties").toString();
        File configFile = new File(configPath);

        FXMLLoader loader;

        // دايماً نفتح شاشة اللوجين أولاً
        // اللوجين هيتعامل مع فحص الاتصال وعرض الكونفيج إذا محتاج
        loader = new FXMLLoader(getClass().getResource("/views/login.fxml"));

        Scene scene = new Scene(loader.load());
        stage.setTitle("Chem Tech - Warehouse Management System");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}