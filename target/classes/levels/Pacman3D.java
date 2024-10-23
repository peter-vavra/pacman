package levels;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.embed.swing.JFXPanel;
import javafx.scene.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Sphere;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import com.jme3.asset.AssetManager;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.gltf.GltfLoader;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.system.JmeSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Pacman3D extends Application {
    Group group = new Group();
    Camera camera = new PerspectiveCamera(true);
    PhongMaterial wallMaterial = new PhongMaterial(Color.BLUE);
    PhongMaterial pacmanMaterial = new PhongMaterial(Color.YELLOW);
    PhongMaterial pelletMaterial = new PhongMaterial(Color.WHITE);
    PhongMaterial halfBlockMaterial = new PhongMaterial(Color.YELLOW);

    private char[][] map;
    private Node pacman;
    private final double pacmanSpeed = 2.0;
    private boolean onHalfBlock = false;
    private Direction currentDirection = Direction.NONE;
    private Direction nextDirection = Direction.NONE;
    private final Map<Sphere, int[]> pelletPositions = new HashMap<>();
    private final List<PelletRespawn> pelletsToRespawn = new ArrayList<>();
    private int score = 0;
    private Text scoreText;

    private enum Direction {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        new JFXPanel(); // Ensures JavaFX environment is initialized
        // Set up the 3D scene
        SubScene subScene = new SubScene(group, 960, 540, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);
        camera.setTranslateX(50);
        camera.setTranslateZ(50);
        camera.setFarClip(2000);

        // Set up the root pane and main scene
        StackPane root = new StackPane();
        Scene scene = new Scene(root, 960, 540);
        root.getChildren().add(subScene);

        loadMapFromFile("levels/level1.txt");
        loadMap();
        setCameraFocusOnPacman();
        setupKeyControls(scene);
        setupScoreText(root);
        startPacmanMovement();

        primaryStage.setTitle("Pacman 3D");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void loadMapFromFile(String resourcePath) {
        try {
            Path path = Paths.get(getClass().getResource("/" + resourcePath).toURI());
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
                System.err.println("No lines found in the file.");
                return;
            }

            map = new char[lines.size()][];
            for (int i = 0; i < lines.size(); i++) {
                map[i] = lines.get(i).toCharArray();
            }
        } catch (Exception e) {
            System.err.println("Error loading the map: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadMap() {
        int Z = 0;
        for (int i = map.length - 1; i >= 0; i--) {
            int X = 0;
            for (int j = 0; j < map[i].length; j++) {
                if (map[i][j] == '#') {
                    createBox(X, Z, wallMaterial);
                } else if (map[i][j] == 'P') {
                    pacman = loadPacmanModel();
                    pacman.setTranslateX(X);
                    pacman.setTranslateZ(Z);
                    group.getChildren().add(pacman);
                    camera.setTranslateX(X);
                    camera.setTranslateZ(Z);
                } else if (map[i][j] == '.') {
                    createPellet(X, Z);
                } else if (map[i][j] == '\'') {
                    createHalfBlock(X, Z);
                }
                X += 50;
            }
            Z += 50;
        }
    }

    private Box createBox(int X, int Z, PhongMaterial material) {
        Box box = new Box(50, 50, 50);
        box.setTranslateX(X);
        box.setTranslateZ(Z);
        box.setMaterial(material);
        group.getChildren().add(box);
        return box;
    }

    private Box createHalfBlock(int X, int Z) {
        Box halfBlock = new Box(50, 25, 50);
        halfBlock.setTranslateX(X);
        halfBlock.setTranslateZ(Z);
        halfBlock.setTranslateY(12.5); // Half height of a full block, translate to be on ground level
        halfBlock.setMaterial(halfBlockMaterial);
        group.getChildren().add(halfBlock);
        return halfBlock;
    }

    private void createPellet(int X, int Z) {
        Sphere pellet = new Sphere(10);
        pellet.setTranslateX(X);
        pellet.setTranslateY(-20); // Make it float
        pellet.setTranslateZ(Z);
        pellet.setMaterial(pelletMaterial);
        group.getChildren().add(pellet);
        pelletPositions.put(pellet, new int[]{X, Z});
    }

    private void respawnPellet(int[] position) {
        createPellet(position[0], position[1]);
    }

    private Node loadPacmanModel() {
        try {
            AssetManager assetManager = JmeSystem.newAssetManager();
            assetManager.registerLocator("src/main/resources/pacman", FileLocator.class);
            Spatial model = assetManager.loadModel("scene.gltf");
            Node node = new Node("pacman");
            node.attachChild(model);
            return node;
        } catch (Exception e) {
            e.printStackTrace();
            return new Node("pacman");
        }
    }

    private void setCameraFocusOnPacman() {
        double offsetDistance = 1000;
        double offsetHeight = 800;

        double angleRadians = Math.toRadians(45);
        double cameraX = pacman.getTranslateX();
        double cameraZ = pacman.getTranslateZ() - offsetDistance * Math.cos(angleRadians);
        double cameraY = pacman.getTranslateY() - offsetHeight;

        camera.setTranslateX(cameraX);
        camera.setTranslateY(cameraY);
        camera.setTranslateZ(cameraZ);

        if (camera instanceof PerspectiveCamera) {
            ((PerspectiveCamera) camera).setFieldOfView(60);
        }

        camera.getTransforms().clear();
        Rotate rotateX = new Rotate(-45, Rotate.X_AXIS);
        camera.getTransforms().add(rotateX);
    }

    private void setupKeyControls(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.W) {
                nextDirection = Direction.UP;
                System.out.println("Next direction: UP");
            } else if (event.getCode() == KeyCode.S) {
                nextDirection = Direction.DOWN;
                System.out.println("Next direction: DOWN");
            } else if (event.getCode() == KeyCode.A) {
                nextDirection = Direction.LEFT;
                System.out.println("Next direction: LEFT");
            } else if (event.getCode() == KeyCode.D) {
                nextDirection = Direction.RIGHT;
                System.out.println("Next direction: RIGHT");
            } else if (event.getCode() == KeyCode.SPACE) {
                if (onHalfBlock) {
                    pacman.setTranslateY(-25); // Move Pacman to the top of the half block
                    onHalfBlock = false;
                }
            }
        });
    }

    private void setupScoreText(StackPane root) {
        scoreText = new Text("Score: " + score);
        scoreText.setFont(new Font(40)); // Larger font size
        scoreText.setFill(Color.WHITE); // White color
        StackPane.setAlignment(scoreText, javafx.geometry.Pos.BOTTOM_LEFT); // Align to bottom left
        scoreText.setTranslateX(20); // Adjust left padding
        scoreText.setTranslateY(-20); // Adjust bottom padding
        root.getChildren().add(scoreText);
    }

    private void updateScore() {
        score++;
        scoreText.setText("Score: " + score);
    }

    private void startPacmanMovement() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                movePacman();
                checkRespawns(now);
            }
        };
        timer.start();
    }

    private void movePacman() {
        if (canMove(nextDirection)) {
            currentDirection = nextDirection;
        }

        double nextX = pacman.getTranslateX();
        double nextZ = pacman.getTranslateZ();

        switch (currentDirection) {
            case UP -> nextZ += pacmanSpeed;
            case DOWN -> nextZ -= pacmanSpeed;
            case LEFT -> nextX -= pacmanSpeed;
            case RIGHT -> nextX += pacmanSpeed;
            default -> {
            }
        }

        if (canMove(currentDirection)) {
            pacman.setTranslateX(nextX);
            pacman.setTranslateZ(nextZ);
            checkPelletCollision();
            updateCameraPosition();
        }
    }

    private void updateCameraPosition() {
        double offsetDistance = 1000;
        double offsetHeight = 800;

        double angleRadians = Math.toRadians(45);
        double cameraX = pacman.getTranslateX();
        double cameraZ = pacman.getTranslateZ() - offsetDistance * Math.cos(angleRadians);
        double cameraY = pacman.getTranslateY() - offsetHeight;

        camera.setTranslateX(cameraX);
        camera.setTranslateY(cameraY);
        camera.setTranslateZ(cameraZ);
    }

    private void checkPelletCollision() {
        Iterator<Node> iterator = group.getChildren().iterator();
        while (iterator.hasNext()) {
            Node node = iterator.next();
            if (node instanceof Sphere && ((Sphere) node).getMaterial() == pelletMaterial) {
                double distance = Math.sqrt(Math.pow(pacman.getTranslateX() - node.getTranslateX(), 2)
                        + Math.pow(pacman.getTranslateZ() - node.getTranslateZ(), 2));

                if (distance < 30) {
                    int[] position = pelletPositions.get(node);
                    iterator.remove();
                    pelletsToRespawn.add(new PelletRespawn(position, System.nanoTime() + 60_000_000_000L));
                    updateScore();
                    break;
                }
            }
        }
    }

    private void checkRespawns(long now) {
        Iterator<PelletRespawn> iterator = pelletsToRespawn.iterator();
        while (iterator.hasNext()) {
            PelletRespawn respawn = iterator.next();
            if (now >= respawn.respawnTime) {
                respawnPellet(respawn.position);
                iterator.remove();
            }
        }
    }

    private boolean canMove(Direction direction) {
        double nextX = pacman.getTranslateX();
        double nextZ = pacman.getTranslateZ();
        double nextY = pacman.getTranslateY();

        switch (direction) {
            case UP:
                nextZ += pacmanSpeed;
                break;
            case DOWN:
                nextZ -= pacmanSpeed;
                break;
            case LEFT:
                nextX -= pacmanSpeed;
                break;
            case RIGHT:
                nextX += pacmanSpeed;
                break;
            default:
                return false;
        }

        double pacmanMinX = nextX - 25;
        double pacmanMaxX = nextX + 25;
        double pacmanMinZ = nextZ - 25;
        double pacmanMaxZ = nextZ + 25;

        for (Node node : group.getChildren()) {
            if (node instanceof Box) {
                Box box = (Box) node;
                double nodeMinX = box.getTranslateX() - 25;
                double nodeMaxX = box.getTranslateX() + 25;
                double nodeMinZ = box.getTranslateZ() - 25;
                double nodeMaxZ = box.getTranslateZ() + 25;

                boolean intersects = pacmanMaxX > nodeMinX && pacmanMinX < nodeMaxX &&
                        pacmanMaxZ > nodeMinZ && pacmanMinZ < nodeMaxZ;

                if (intersects) {
                    if (box.getMaterial() == wallMaterial) {
                        return false;
                    } else if (box.getMaterial() == halfBlockMaterial) {
                        if (nextY == 0) {
                            onHalfBlock = true;
                            return false;
                        }
                    }
                }
            }
        }

        onHalfBlock = false;
        return true;
    }

    private static class PelletRespawn {
        int[] position;
        long respawnTime;

        PelletRespawn(int[] position, long respawnTime) {
            this.position = position;
            this.respawnTime = respawnTime;
        }
    }
}
