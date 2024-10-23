package com.example.pacman3d;

import javafx.animation.AnimationTimer;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.*;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.transform.Rotate;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import com.interactivemesh.jfx.importer.obj.ObjModelImporter;

/**
 * Main class for the Pacman 3D game.
 */
public class Pacman3D extends Application {

    private MediaPlayer mediaPlayer;
    private MediaPlayer gameOverMediaPlayer;

    Group group = new Group();
    Camera camera = new PerspectiveCamera(true);

    PhongMaterial wallMaterial = new PhongMaterial(Color.TRANSPARENT);
    PhongMaterial edgeMaterial = new PhongMaterial(Color.BLUE);
    PhongMaterial pelletMaterial = new PhongMaterial(Color.WHITE);
    PhongMaterial halfBlockMaterial = new PhongMaterial(Color.YELLOW);
    PhongMaterial gateMaterial = new PhongMaterial(Color.YELLOW);

    private char[][] map;
    private Group pacman;
    private final double pacmanSpeed = 2.0;
    private boolean onHalfBlock = false;

    private Direction currentDirection = Direction.NONE;
    private Direction nextDirection = Direction.NONE;

    private final Map<Sphere, int[]> pelletPositions = new HashMap<>();
    private final Map<Sphere, int[]> bonusFruitPositions = new HashMap<>();
    private final List<PelletRespawn> pelletsToRespawn = new ArrayList<>();

    private int score = 0;
    private Text scoreValueText;
    private final List<Shape> lifeIndicators = new ArrayList<>();

    private final List<Box> ghostGates = new ArrayList<>();
    private final List<Ghost> ghosts = new ArrayList<>();
    private boolean gatesOpened = false;
    private boolean gatesClosed = false;
    private boolean ghostsCanMove = false;
    private boolean ghostEatable = false;
    private long ghostEatableStartTime = 0;
    private Text ghostEatableTimer;

    private MediaPlayer deathMediaPlayer;
    private int lives = 3;
    private Text gameOverText;
    private Button playAgainButton;

    private final Map<Sphere, Long> bonusFruitSpawnTimes = new HashMap<>();

    /**
     * Enumeration for direction of movement.
     */
    private enum Direction {
        UP, DOWN, LEFT, RIGHT, NONE
    }

    /**
     * Main method to launch the application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Media chompSound = new Media(Objects.requireNonNull(getClass().getResource("/music/pacman_chomp.mp3")).toExternalForm());
        mediaPlayer = new MediaPlayer(chompSound);

        Media deathSound = new Media(Objects.requireNonNull(getClass().getResource("/music/pacman_death.mp3")).toExternalForm());
        deathMediaPlayer = new MediaPlayer(deathSound);

        Media gameOverSound = new Media(Objects.requireNonNull(getClass().getResource("/music/game_over.mp3")).toExternalForm());
        gameOverMediaPlayer = new MediaPlayer(gameOverSound);

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        double screenWidth = screenBounds.getWidth();
        double screenHeight = screenBounds.getHeight();

        SubScene subScene = new SubScene(group, screenWidth, screenHeight, true, SceneAntialiasing.BALANCED);
        subScene.setFill(Color.BLACK);
        subScene.setCamera(camera);
        camera.setTranslateX(50);
        camera.setTranslateZ(50);
        camera.setFarClip(2000);

        StackPane root = new StackPane();
        Scene scene = new Scene(root, screenWidth, screenHeight);
        root.getChildren().add(subScene);

        subScene.widthProperty().bind(root.widthProperty());
        subScene.heightProperty().bind(root.heightProperty());

        loadMapFromFile();
        loadMap();
        setCameraFocusOnPacman();
        setupKeyControls(scene);
        setupScoreText(root, screenWidth, screenHeight);
        setupLifeIndicators(root, screenWidth, screenHeight);
        setupGameOverUI(root, screenHeight);
        setupGhostEatableTimer(root, screenHeight);
        startPacmanMovement();

        primaryStage.setTitle("Pacman 3D");
        primaryStage.setScene(scene);
        primaryStage.setFullScreen(true);
        primaryStage.show();

        primaryStage.fullScreenProperty().addListener((obs, wasFullScreen, isNowFullScreen) -> {
            if (!isNowFullScreen) {
                primaryStage.setWidth(1024);
                primaryStage.setHeight(768);
                primaryStage.centerOnScreen();
            }
        });
    }

    /**
     * Loads the game map from a file.
     */
    private void loadMapFromFile() {
        try {
            java.nio.file.Path path = Paths.get(Objects.requireNonNull(getClass().getResource("/levels/level1.txt")).toURI());
            List<String> lines = Files.readAllLines(path);
            if (lines.isEmpty()) {
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

    /**
     * Loads the map and initializes game objects.
     */
    private void loadMap() {
        int Z = 0;
        for (int i = map.length - 1; i >= 0; i--) {
            int X = 0;
            for (int j = 0; j < map[i].length; j++) {
                if (map[i][j] == '#') {
                    createBox(X, Z, wallMaterial, edgeMaterial);
                } else if (map[i][j] == 'P') {
                    loadPacmanModel(X, Z);
                    camera.setTranslateX(X);
                    camera.setTranslateZ(Z);
                } else if (map[i][j] == '.') {
                    createPellet(X, Z);
                } else if (map[i][j] == '\'') {
                    createHalfBlock(X, Z);
                } else if (map[i][j] == '-') {
                    createGhostGate(X, Z);
                } else if (map[i][j] == '1' || map[i][j] == '2') {
                    Ghost ghost = new Ghost(loadGhostModel(map[i][j], X, Z), X, Z, map[i][j]);
                    ghosts.add(ghost);
                }
                X += 50;
            }
            Z += 50;
        }
    }

    /**
     * Sets up the game over UI elements.
     * @param root the root pane
     * @param screenHeight the height of the screen
     */
    private void setupGameOverUI(StackPane root, double screenHeight) {
        gameOverText = new Text("GAME OVER");
        gameOverText.setFont(new Font("Verdana", screenHeight * 0.1));
        gameOverText.setFill(Color.RED);
        gameOverText.setVisible(false);

        playAgainButton = new Button("Play Again");
        playAgainButton.setFont(new Font("Verdana", screenHeight * 0.05));
        playAgainButton.setVisible(false);
        playAgainButton.setOnAction(event -> resetGame());

        StackPane.setAlignment(gameOverText, Pos.CENTER);
        StackPane.setAlignment(playAgainButton, Pos.CENTER);
        playAgainButton.setTranslateY(screenHeight * 0.15);

        root.getChildren().addAll(gameOverText, playAgainButton);
    }

    /**
     * Sets up the ghost eatable timer UI element.
     * @param root the root pane
     * @param screenHeight the height of the screen
     */
    private void setupGhostEatableTimer(StackPane root, double screenHeight) {
        ghostEatableTimer = new Text();
        ghostEatableTimer.setFont(new Font("Verdana", screenHeight * 0.05));
        ghostEatableTimer.setFill(Color.GREEN);
        ghostEatableTimer.setVisible(false);

        StackPane.setAlignment(ghostEatableTimer, Pos.TOP_CENTER);
        ghostEatableTimer.setTranslateY(screenHeight * 0.02);

        root.getChildren().add(ghostEatableTimer);
    }

    /**
     * Creates a box object (wall).
     * @param X the x-coordinate
     * @param Z the z-coordinate
     * @param wallMaterial the material for the wall
     * @param edgeMaterial the material for the edges
     */
    private void createBox(int X, int Z, Material wallMaterial, Material edgeMaterial) {
        Box box = new Box(50, 50, 50);
        box.setTranslateX(X);
        box.setTranslateZ(Z);
        box.setMaterial(wallMaterial);
        group.getChildren().add(box);

        if (edgeMaterial != null) {
            Box edgeBox = new Box(50, 50, 50);
            edgeBox.setTranslateX(X);
            edgeBox.setTranslateZ(Z);
            edgeBox.setMaterial(edgeMaterial);
            edgeBox.setDrawMode(DrawMode.LINE);
            group.getChildren().add(edgeBox);
        }
    }

    /**
     * Creates a half-block object.
     * @param X the x-coordinate
     * @param Z the z-coordinate
     */
    private void createHalfBlock(int X, int Z) {
        Box halfBlock = new Box(50, 25, 50);
        halfBlock.setTranslateX(X);
        halfBlock.setTranslateZ(Z);
        halfBlock.setTranslateY(12.5);
        halfBlock.setMaterial(halfBlockMaterial);
        group.getChildren().add(halfBlock);
    }

    /**
     * Creates a ghost gate object.
     * @param X the x-coordinate
     * @param Z the z-coordinate
     */
    private void createGhostGate(int X, int Z) {
        Box gate = new Box(50, 25, 50);
        gate.setTranslateX(X);
        gate.setTranslateZ(Z);
        gate.setTranslateY(-12.5);
        gate.setMaterial(gateMaterial);
        group.getChildren().add(gate);
        ghostGates.add(gate);
    }

    /**
     * Loads a ghost model from an OBJ file.
     * @param type the type of ghost
     * @param X the x-coordinate
     * @param Z the z-coordinate
     * @return the loaded ghost model
     */
    private Group loadGhostModel(char type, int X, int Z) {
        ObjModelImporter importer = new ObjModelImporter();
        URL modelUrl = type == '1' ? getClass().getResource("/model3D/ghost.obj") : getClass().getResource("/model3D/ghost1.obj");
        if (modelUrl == null) {
            throw new RuntimeException("Model file not found at: " + (type == '1' ? "/model3D/ghost.obj" : "/model3D/ghost1.obj"));
        }
        importer.read(modelUrl);
        Group ghostModel = new Group(importer.getImport());

        // Correcting the orientation by rotating the model
        Rotate rotateX = new Rotate(270, Rotate.X_AXIS); // Rotate 180 degrees around X-axis
        ghostModel.getTransforms().add(rotateX);

        ghostModel.setTranslateX(X);
        ghostModel.setTranslateZ(Z);
        double scale = 50 / ghostModel.getBoundsInParent().getWidth();
        ghostModel.setScaleX(scale);
        ghostModel.setScaleY(scale);
        ghostModel.setScaleZ(scale);
        group.getChildren().add(ghostModel);
        return ghostModel;
    }

    /**
     * Opens the ghost gates.
     */
    private void openGhostGates() {
        for (Box gate : ghostGates) {
            if (gate.getTranslateY() == -12.5) {
                TranslateTransition transition = new TranslateTransition(Duration.seconds(1), gate);
                transition.setByY(-25);
                transition.play();
            }
        }
        gatesOpened = true;
    }

    /**
     * Closes the ghost gates.
     */
    private void closeGhostGates() {
        for (Box gate : ghostGates) {
            if (gate.getTranslateY() != -12.5) {
                TranslateTransition transition = new TranslateTransition(Duration.seconds(1), gate);
                transition.setByY(25);
                transition.play();
            }
        }
        gatesClosed = true;
    }

    /**
     * Sets the camera focus on Pacman.
     */
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

    /**
     * Sets up key controls for the game.
     * @param scene the main scene
     */
    private void setupKeyControls(Scene scene) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.W) {
                nextDirection = Direction.UP;
            } else if (event.getCode() == KeyCode.S) {
                nextDirection = Direction.DOWN;
            } else if (event.getCode() == KeyCode.A) {
                nextDirection = Direction.LEFT;
            } else if (event.getCode() == KeyCode.D) {
                nextDirection = Direction.RIGHT;
            } else if (event.getCode() == KeyCode.SPACE) {
                if (!onHalfBlock && isAdjacentToHalfBlock(pacman.getTranslateX(), pacman.getTranslateZ())) {
                    pacman.setTranslateY(-25);
                    onHalfBlock = true;
                }
            }
        });
    }

    /**
     * Sets up the score text UI element.
     * @param root the root pane
     * @param screenWidth the width of the screen
     * @param screenHeight the height of the screen
     */
    private void setupScoreText(StackPane root, double screenWidth, double screenHeight) {
        Text scoreText = new Text("SCORE");
        scoreText.setFont(new Font("Verdana", screenHeight * 0.05));
        scoreText.setFill(Color.WHITE);

        scoreValueText = new Text("     " + score);
        scoreValueText.setFont(new Font("Verdana", screenHeight * 0.05));
        scoreValueText.setFill(Color.YELLOW);

        StackPane.setAlignment(scoreText, javafx.geometry.Pos.BOTTOM_LEFT);
        StackPane.setAlignment(scoreValueText, javafx.geometry.Pos.BOTTOM_LEFT);
        scoreText.setTranslateX(screenWidth * 0.02);
        scoreText.setTranslateY(-screenHeight * 0.02);
        scoreValueText.setTranslateX(screenWidth * 0.10);
        scoreValueText.setTranslateY(-screenHeight * 0.02);

        root.getChildren().addAll(scoreText, scoreValueText);
    }

    /**
     * Sets up the life indicators UI elements.
     * @param root the root pane
     * @param screenWidth the width of the screen
     * @param screenHeight the height of the screen
     */
    private void setupLifeIndicators(StackPane root, double screenWidth, double screenHeight) {
        double heartSize = screenHeight * 0.05;
        int lives = 3;
        for (int i = 0; i < lives; i++) {
            Shape heart = createHeartShape(heartSize);
            heart.setTranslateX(-screenWidth * 0.02 - i * (heartSize + 10));
            heart.setTranslateY(-screenHeight * 0.02);
            StackPane.setAlignment(heart, javafx.geometry.Pos.BOTTOM_RIGHT);
            lifeIndicators.add(heart);
            root.getChildren().add(heart);
        }
    }

    /**
     * Creates a heart shape for life indicators.
     * @param size the size of the heart
     * @return the created heart shape
     */
    private Shape createHeartShape(double size) {
        double x = size / 2;
        double y = size / 3;
        double controlX = size / 4;
        double controlY = 0;

        javafx.scene.shape.Path path = new javafx.scene.shape.Path();
        path.getElements().add(new MoveTo(x, y));
        path.getElements().add(new CubicCurveTo(controlX, controlY, 0, controlY, 0, y));
        path.getElements().add(new CubicCurveTo(0, controlY + y, controlX, 2 * y, x, size));
        path.getElements().add(new CubicCurveTo(controlX + x, 2 * y, size, controlY + y, size, y));
        path.getElements().add(new CubicCurveTo(size, controlY, controlX + x, controlY, x, y));
        path.setFill(Color.RED);
        path.setStroke(Color.WHITE);
        return path;
    }

    /**
     * Loads the Pacman model from an OBJ file.
     * @param X the x-coordinate
     * @param Z the z-coordinate
     */
    private void loadPacmanModel(int X, int Z) {
        ObjModelImporter importer = new ObjModelImporter();
        URL modelUrl = getClass().getResource("/model3D/pacisback.obj");
        if (modelUrl == null) {
            throw new RuntimeException("Model file not found at: /model3D/pacisback.obj");
        }
        importer.read(modelUrl);
        Group pacmanModel = new Group(importer.getImport());
        pacmanModel.setTranslateX(X);
        pacmanModel.setTranslateZ(Z);
        double scale = 50 / pacmanModel.getBoundsInParent().getWidth();
        pacmanModel.setScaleX(scale);
        pacmanModel.setScaleY(scale);
        pacmanModel.setScaleZ(scale);
        group.getChildren().add(pacmanModel);
        pacman = pacmanModel;
    }

    /**
     * Creates a pellet object.
     * @param X the x-coordinate
     * @param Z the z-coordinate
     */
    private void createPellet(int X, int Z) {
        Sphere pellet = new Sphere(10);
        pellet.setTranslateX(X);
        pellet.setTranslateY(-20);
        pellet.setTranslateZ(Z);
        pellet.setMaterial(pelletMaterial);
        group.getChildren().add(pellet);
        pelletPositions.put(pellet, new int[]{X, Z});
    }

    /**
     * Creates a bonus fruit object.
     * @param x the x-coordinate
     * @param z the z-coordinate
     * @return the created bonus fruit object
     */
    private Sphere createBonusFruit(int x, int z) {
        Sphere bonusFruit = new Sphere(15);  // Bonus fruit is larger than regular pellet
        bonusFruit.setTranslateX(x);
        bonusFruit.setTranslateY(-20);
        bonusFruit.setTranslateZ(z);
        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(Color.YELLOW);
        bonusFruit.setMaterial(material);
        group.getChildren().add(bonusFruit);
        return bonusFruit;
    }

    /**
     * Respawns a pellet at the given position.
     * @param position the position to respawn the pellet
     */
    private void respawnPellet(int[] position) {
        createPellet(position[0], position[1]);
    }

    /**
     * Updates the score by 1 point.
     */
    private void updateScore() {
        score++;
        scoreValueText.setText("     " + score);
    }

    /**
     * Updates the score by the given number of points.
     * @param points the number of points to add to the score
     */
    private void updateScore(int points) {
        score += points;
        scoreValueText.setText("     " + score);
    }

    /**
     * Starts the Pacman movement and handles game logic.
     */
    private void startPacmanMovement() {
        AnimationTimer timer = new AnimationTimer() {
            private long gateOpenTime = 0;
            private long lastBonusFruitSpawnTime = 0;

            @Override
            public void handle(long now) {
                movePacman();
                if (gatesOpened && !gatesClosed && now - gateOpenTime >= 5_000_000_000L) {
                    closeGhostGates();
                    gateOpenTime = now;
                } else if (!gatesOpened && (currentDirection != Direction.NONE || nextDirection != Direction.NONE)) {
                    openGhostGates();
                    gatesOpened = true;
                    gateOpenTime = now;
                } else if (gatesOpened && !ghostsCanMove && now - gateOpenTime >= 1_000_000_000L) {
                    ghostsCanMove = true;
                }
                if (ghostsCanMove) {
                    moveGhosts(now);
                }
                checkRespawns(now);
                checkFallOffHalfBlock();
                checkGhostCollision();
                updateGhostEatableTimer(now);

                // Spawn bonus fruit every 10 seconds
                if (now - lastBonusFruitSpawnTime >= 10_000_000_000L) {
                    spawnBonusFruit();
                    lastBonusFruitSpawnTime = now;
                }

                // Check and remove expired bonus fruits
                checkBonusFruitExpiry(now);
            }
        };
        timer.start();
    }

    /**
     * Checks for expired bonus fruits and removes them.
     * @param now the current time
     */
    private void checkBonusFruitExpiry(long now) {
        Iterator<Map.Entry<Sphere, Long>> iterator = bonusFruitSpawnTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Sphere, Long> entry = iterator.next();
            Sphere bonusFruit = entry.getKey();
            long spawnTime = entry.getValue();
            if (now - spawnTime >= 10_000_000_000L) {  // 10 seconds
                int[] position = bonusFruitPositions.get(bonusFruit);
                group.getChildren().remove(bonusFruit);
                respawnPellet(position);
                iterator.remove();
                bonusFruitPositions.remove(bonusFruit);
            }
        }
    }

    /**
     * Checks for collisions between Pacman and ghosts.
     */
    private void checkGhostCollision() {
        List<Ghost> collidedGhosts = new ArrayList<>(); // List to keep track of collided ghosts
        for (Ghost ghost : ghosts) {
            Group ghostModel = ghost.getGhostModel();
            double distance = Math.sqrt(Math.pow(pacman.getTranslateX() - ghostModel.getTranslateX(), 2)
                    + Math.pow(pacman.getTranslateZ() - ghostModel.getTranslateZ(), 2));
            if (distance < 30) {
                collidedGhosts.add(ghost); // Add collided ghost to the list
            }
        }
        for (Ghost ghost : collidedGhosts) {
            if (ghostEatable) {
                // Remove ghost and respawn at original position
                group.getChildren().remove(ghost.getGhostModel());
                ghosts.remove(ghost);
                respawnGhost(ghost);
                openGhostGates(); // Open the ghost gates
            } else {
                handlePacmanDeath();
                break;
            }
        }
    }

    /**
     * Respawns a ghost at its original position.
     * @param ghost the ghost to respawn
     */
    private void respawnGhost(Ghost ghost) {
        Group ghostModel = loadGhostModel(ghost.getType(), ghost.getStartX(), ghost.getStartZ());
        Ghost newGhost = new Ghost(ghostModel, ghost.getStartX(), ghost.getStartZ(), ghost.getType());
        ghosts.add(newGhost);

        // Delay movement for 1 second
        newGhost.setMoveStartTime(System.nanoTime() + 1_000_000_000L);
    }

    /**
     * Handles Pacman's death.
     */
    private void handlePacmanDeath() {
        lives--;
        updateLifeIndicators();
        score = 0; // Reset score
        scoreValueText.setText("     " + score);
        if (lives > 0) {
            resetPacmanPosition();
            playDeathSound();
        } else {
            showGameOver();
        }
    }

    /**
     * Displays the game over UI.
     */
    private void showGameOver() {
        gameOverText.setVisible(true);
        playAgainButton.setVisible(true);
        playGameOverSound(); // Play game over sound
        stopGame();
    }

    /**
     * Stops the game.
     */
    private void stopGame() {
        currentDirection = Direction.NONE;
        nextDirection = Direction.NONE;
    }

    /**
     * Resets the game state for a new game.
     */
    private void resetGame() {
        lives = 3;

        for (Shape heart : lifeIndicators) {
            heart.setFill(Color.RED);
        }

        gameOverText.setVisible(false);
        playAgainButton.setVisible(false);

        score = 0;
        scoreValueText.setText("     " + score);

        resetPacmanPosition();
        startPacmanMovement();
    }

    /**
     * Resets Pacman's position to its starting point.
     */
    private void resetPacmanPosition() {
        for (int i = 0; i < map.length; i++) {
            for (int j = 0; j < map[i].length; j++) {
                if (map[i][j] == 'P') {
                    pacman.setTranslateX(j * 50);
                    pacman.setTranslateZ((map.length - 1 - i) * 50);
                    setCameraFocusOnPacman();
                    return;
                }
            }
        }
    }

    /**
     * Updates the life indicators UI.
     */
    private void updateLifeIndicators() {
        if (lives >= 0 && lives < lifeIndicators.size()) {
            Shape heart = lifeIndicators.get(lifeIndicators.size() - 1 - lives);
            heart.setFill(Color.GRAY);
        }
    }

    /**
     * Plays the death sound.
     */
    private void playDeathSound() {
        deathMediaPlayer.stop();
        deathMediaPlayer.play();
    }

    /**
     * Plays the game over sound.
     */
    private void playGameOverSound() {
        if (gameOverMediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
            gameOverMediaPlayer.stop();
            gameOverMediaPlayer.play();
        }
    }

    /**
     * Moves the ghosts.
     * @param now the current time
     */
    private void moveGhosts(long now) {
        for (int i = 0; i < ghosts.size(); i++) {
            Ghost ghost = ghosts.get(i);
            Group ghostModel = ghost.getGhostModel();
            Direction direction = ghost.getDirection();

            if (ghost.isInitialMove()) {
                if (ghost.getMoveStartTime() == 0) {
                    ghost.setMoveStartTime(now + i * 500_000_000L);
                }
                if (now >= ghost.getMoveStartTime()) {
                    if (canMoveGhost(ghostModel, direction)) {
                        moveGhost(ghostModel, direction);
                    } else {
                        if (direction == Direction.UP) {
                            ghost.setStopTime(now + 500_000_000L);
                            ghost.setInitialMove(false);
                            ghost.setAssignedInitialDirection(true);
                        }
                    }
                }
            } else if (ghost.isAssignedInitialDirection() && now < ghost.getStopTime()) {
            } else if (ghost.isAssignedInitialDirection() && now >= ghost.getStopTime()) {
                if (!ghost.isDirectionSetAfterStop()) {
                    if (i == 0) {
                        ghost.setDirection(Direction.LEFT);
                    } else if (i == 1) {
                        ghost.setDirection(Direction.RIGHT);
                    }
                    ghost.setDirectionSetAfterStop(true);
                }

                if (canMoveGhost(ghostModel, direction)) {
                    moveGhost(ghostModel, direction);
                } else {
                    direction = getNextDirection(ghostModel, direction);
                    ghost.setDirection(direction);
                    if (canMoveGhost(ghostModel, direction)) {
                        moveGhost(ghostModel, direction);
                    }
                }
            } else {
                if (isIntersection(ghostModel)) {
                    direction = getNextDirection(ghostModel, direction);
                    ghost.setDirection(direction);
                }
                if (canMoveGhost(ghostModel, direction)) {
                    moveGhost(ghostModel, direction);
                } else {
                    direction = getNextDirection(ghostModel, direction);
                    ghost.setDirection(direction);
                    if (canMoveGhost(ghostModel, direction)) {
                        moveGhost(ghostModel, direction);
                    }
                }
            }
        }
    }

    /**
     * Checks if a ghost is at an intersection.
     * @param ghostModel the ghost's model
     * @return true if the ghost is at an intersection, false otherwise
     */
    private boolean isIntersection(Group ghostModel) {
        int validMoves = 0;
        for (Direction direction : Direction.values()) {
            if (direction != Direction.NONE && canMoveGhost(ghostModel, direction)) {
                validMoves++;
            }
        }
        return validMoves > 2;
    }

    /**
     * Checks if a ghost can move in a given direction.
     * @param ghostModel the ghost's model
     * @param direction the direction to move
     * @return true if the ghost can move, false otherwise
     */
    private boolean canMoveGhost(Group ghostModel, Direction direction) {
        double nextX = ghostModel.getTranslateX();
        double nextZ = ghostModel.getTranslateZ();

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

        double ghostMinX = nextX - 25;
        double ghostMaxX = nextX + 25;
        double ghostMinZ = nextZ - 25;
        double ghostMaxZ = nextZ + 25;

        for (Node node : group.getChildren()) {
            if (node instanceof Box box) {
                double nodeMinX = box.getTranslateX() - 25;
                double nodeMaxX = box.getTranslateX() + 25;
                double nodeMinZ = box.getTranslateZ() - 25;
                double nodeMaxZ = box.getTranslateZ() + 25;

                boolean intersects = ghostMaxX > nodeMinX && ghostMinX < nodeMaxX &&
                        ghostMaxZ > nodeMinZ && ghostMinZ < nodeMaxZ;

                if (intersects) {
                    if (box.getMaterial() == wallMaterial || box.getMaterial() == halfBlockMaterial) {
                        return false;
                    } else if (box.getMaterial() == gateMaterial && box.getTranslateY() != -37.5) {
                        return false;
                    }
                }
            }
        }

        for (Ghost ghost : ghosts) {
            if (ghost.getGhostModel() != ghostModel) {
                Group otherGhostModel = ghost.getGhostModel();
                double otherGhostMinX = otherGhostModel.getTranslateX() - 25;
                double otherGhostMaxX = otherGhostModel.getTranslateX() + 25;
                double otherGhostMinZ = otherGhostModel.getTranslateZ() - 25;
                double otherGhostMaxZ = otherGhostModel.getTranslateZ() + 25;

                boolean intersects = ghostMaxX > otherGhostMinX && ghostMinX < otherGhostMaxX &&
                        ghostMaxZ > otherGhostMinZ && ghostMinZ < otherGhostMaxZ;

                if (intersects) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Moves a ghost in a given direction.
     * @param ghostModel the ghost's model
     * @param direction the direction to move
     */
    private void moveGhost(Group ghostModel, Direction direction) {
        double nextX = ghostModel.getTranslateX();
        double nextZ = ghostModel.getTranslateZ();

        switch (direction) {
            case UP -> {
                nextZ += pacmanSpeed;
                ghostModel.setRotationAxis(Rotate.Y_AXIS);
                ghostModel.setRotate(180);
            }
            case DOWN -> {
                nextZ -= pacmanSpeed;
                ghostModel.setRotationAxis(Rotate.Y_AXIS);
                ghostModel.setRotate(0);
            }
            case LEFT -> {
                nextX -= pacmanSpeed;
                ghostModel.setRotationAxis(Rotate.Y_AXIS);
                ghostModel.setRotate(90);
            }
            case RIGHT -> {
                nextX += pacmanSpeed;
                ghostModel.setRotationAxis(Rotate.Y_AXIS);
                ghostModel.setRotate(-90);
            }
            default -> {
            }
        }

        ghostModel.setTranslateX(nextX);
        ghostModel.setTranslateZ(nextZ);
    }

    /**
     * Gets the next direction for a ghost based on the current direction.
     * @param ghostModel the ghost's model
     * @param currentDirection the current direction of the ghost
     * @return the next direction for the ghost
     */
    private Direction getNextDirection(Group ghostModel, Direction currentDirection) {
        List<Direction> possibleDirections = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (direction != Direction.NONE && canMoveGhost(ghostModel, direction)) {
                possibleDirections.add(direction);
            }
        }

        possibleDirections.remove(getOppositeDirection(currentDirection));

        if (possibleDirections.isEmpty()) {
            return getOppositeDirection(currentDirection);
        }

        return possibleDirections.get(new Random().nextInt(possibleDirections.size()));
    }

    /**
     * Gets the opposite direction of the given direction.
     * @param direction the current direction
     * @return the opposite direction
     */
    private Direction getOppositeDirection(Direction direction) {
        return switch (direction) {
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
            case LEFT -> Direction.RIGHT;
            case RIGHT -> Direction.LEFT;
            default -> Direction.NONE;
        };
    }

    /**
     * Moves Pacman.
     */
    private void movePacman() {
        if (canMove(nextDirection)) {
            currentDirection = nextDirection;
        }

        double nextX = pacman.getTranslateX();
        double nextZ = pacman.getTranslateZ();

        switch (currentDirection) {
            case UP -> {
                nextZ += pacmanSpeed;
                pacman.setRotationAxis(Rotate.Y_AXIS);
                pacman.setRotate(180);
            }
            case DOWN -> {
                nextZ -= pacmanSpeed;
                pacman.setRotationAxis(Rotate.Y_AXIS);
                pacman.setRotate(0);
            }
            case LEFT -> {
                nextX -= pacmanSpeed;
                pacman.setRotationAxis(Rotate.Y_AXIS);
                pacman.setRotate(90);
            }
            case RIGHT -> {
                nextX += pacmanSpeed;
                pacman.setRotationAxis(Rotate.Y_AXIS);
                pacman.setRotate(-90);
            }
            default -> {
            }
        }

        if (canMove(currentDirection)) {
            pacman.setTranslateX(nextX);
            pacman.setTranslateZ(nextZ);
            checkPelletCollision();
            checkBonusFruitCollision();
            updateCameraPosition();
        }
    }

    /**
     * Checks if Pacman has fallen off a half-block.
     */
    private void checkFallOffHalfBlock() {
        if (onHalfBlock && !isOnHalfBlock(pacman.getTranslateX(), pacman.getTranslateZ())) {
            pacman.setTranslateY(0);
            onHalfBlock = false;
        }
    }

    /**
     * Updates the camera position based on Pacman's position.
     */
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

    /**
     * Checks for collisions between Pacman and pellets.
     */
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
                    playChompSound();
                    break;
                }
            }
        }
    }

    /**
     * Checks for collisions between Pacman and bonus fruits.
     */
    private void checkBonusFruitCollision() {
        Iterator<Map.Entry<Sphere, int[]>> iterator = bonusFruitPositions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Sphere, int[]> entry = iterator.next();
            Sphere bonusFruit = entry.getKey();
            double distance = Math.sqrt(Math.pow(pacman.getTranslateX() - bonusFruit.getTranslateX(), 2)
                    + Math.pow(pacman.getTranslateZ() - bonusFruit.getTranslateZ(), 2));

            if (distance < 30) {
                int[] position = entry.getValue();
                group.getChildren().remove(bonusFruit);
                ghostEatable = true;
                ghostEatableStartTime = System.nanoTime();
                updateScore(50); // Bonus fruit gives 50 points
                respawnPellet(position);
                iterator.remove();
                bonusFruitPositions.remove(bonusFruit);
                bonusFruitSpawnTimes.remove(bonusFruit);
                break;
            }
        }
    }

    /**
     * Plays the chomp sound.
     */
    private void playChompSound() {
        mediaPlayer.stop();
        mediaPlayer.play();
    }

    /**
     * Checks for respawn times of pellets and respawns them.
     * @param now the current time
     */
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

    /**
     * Spawns a bonus fruit at a random position.
     */
    private void spawnBonusFruit() {
        List<int[]> availablePositions = new ArrayList<>(pelletPositions.values());
        if (!availablePositions.isEmpty()) {
            int[] position = availablePositions.get(new Random().nextInt(availablePositions.size()));
            Sphere bonusFruit = createBonusFruit(position[0], position[1]);
            bonusFruitPositions.put(bonusFruit, position);
            bonusFruitSpawnTimes.put(bonusFruit, System.nanoTime());
        }
    }

    /**
     * Updates the ghost eatable timer.
     * @param now the current time
     */
    private void updateGhostEatableTimer(long now) {
        if (ghostEatable) {
            long elapsed = (now - ghostEatableStartTime) / 1_000_000_000L;
            long remaining = 10 - elapsed;
            if (remaining > 0) {
                ghostEatableTimer.setText("Ghosts are eatable for next " + remaining);
                ghostEatableTimer.setVisible(true);
            } else {
                ghostEatable = false;
                ghostEatableTimer.setVisible(false);
            }
        }
    }

    /**
     * Checks if Pacman can move in a given direction.
     * @param direction the direction to move
     * @return true if Pacman can move, false otherwise
     */
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

        boolean isCollidingWithWall = false;
        boolean isCollidingWithHalfBlock = false;
        boolean isCollidingWithClosedGate = false;

        for (Node node : group.getChildren()) {
            if (node instanceof Box box) {
                double nodeMinX = box.getTranslateX() - 25;
                double nodeMaxX = box.getTranslateX() + 25;
                double nodeMinZ = box.getTranslateZ() - 25;
                double nodeMaxZ = box.getTranslateZ() + 25;

                boolean intersects = pacmanMaxX > nodeMinX && pacmanMinX < nodeMaxX &&
                        pacmanMaxZ > nodeMinZ && pacmanMinZ < nodeMaxZ;

                if (intersects) {
                    if (box.getMaterial() == wallMaterial) {
                        isCollidingWithWall = true;
                    } else if (box.getMaterial() == halfBlockMaterial) {
                        isCollidingWithHalfBlock = true;
                    } else if (box.getMaterial() == gateMaterial && box.getTranslateY() == -12.5) {
                        isCollidingWithClosedGate = true;
                    }
                }
            }
        }

        if (isCollidingWithWall || isCollidingWithClosedGate) {
            return false;
        }

        if (isCollidingWithHalfBlock) {
            if (nextY == 0) {
                return false;
            } else if (nextY == -25) {
                pacman.setTranslateY(-25);
                onHalfBlock = true;
                return true;
            }
        } else {
            if (onHalfBlock) {
                pacman.setTranslateY(0);
                onHalfBlock = false;
            }
        }

        return true;
    }

    /**
     * Checks if Pacman is on a half-block.
     * @param x the x-coordinate
     * @param z the z-coordinate
     * @return true if Pacman is on a half-block, false otherwise
     */
    private boolean isOnHalfBlock(double x, double z) {
        for (Node node : group.getChildren()) {
            if (node instanceof Box halfBlock && ((Box) node).getMaterial() == halfBlockMaterial) {
                double minX = halfBlock.getTranslateX() - 50;
                double maxX = halfBlock.getTranslateX() + 50;
                double minZ = halfBlock.getTranslateZ() - 50;
                double maxZ = halfBlock.getTranslateZ() + 50;
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if Pacman is adjacent to a half-block.
     * @param x the x-coordinate
     * @param z the z-coordinate
     * @return true if Pacman is adjacent to a half-block, false otherwise
     */
    private boolean isAdjacentToHalfBlock(double x, double z) {
        for (Node node : group.getChildren()) {
            if (node instanceof Box halfBlock && ((Box) node).getMaterial() == halfBlockMaterial) {
                double minX = halfBlock.getTranslateX() - 75;
                double maxX = halfBlock.getTranslateX() + 75;
                double minZ = halfBlock.getTranslateZ() - 75;
                double maxZ = halfBlock.getTranslateZ() + 75;
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Inner class representing a pellet respawn event.
     */
    private static class PelletRespawn {
        int[] position;
        long respawnTime;

        /**
         * Constructor for PelletRespawn.
         * @param position the position to respawn the pellet
         * @param respawnTime the time to respawn the pellet
         */
        PelletRespawn(int[] position, long respawnTime) {
            this.position = position;
            this.respawnTime = respawnTime;
        }
    }

    /**
     * Inner class representing a ghost.
     */
    private static class Ghost {
        private final Group ghostModel;
        private Direction direction;
        private boolean initialMove;
        private boolean assignedInitialDirection;
        private long moveStartTime;
        private long stopTime;
        private boolean directionSetAfterStop;
        private final int startX;
        private final int startZ;
        private final char type;

        /**
         * Constructor for Ghost.
         * @param ghostModel the 3D model of the ghost
         * @param startX the starting X coordinate
         * @param startZ the starting Z coordinate
         * @param type the type of ghost
         */
        public Ghost(Group ghostModel, int startX, int startZ, char type) {
            this.ghostModel = ghostModel;
            this.direction = Direction.UP;
            this.initialMove = true;
            this.assignedInitialDirection = false;
            this.moveStartTime = 0;
            this.stopTime = 0;
            this.directionSetAfterStop = false;
            this.startX = startX;
            this.startZ = startZ;
            this.type = type;
        }

        /**
         * Gets the 3D model of the ghost.
         * @return the 3D model of the ghost
         */
        public Group getGhostModel() {
            return ghostModel;
        }

        /**
         * Gets the direction of the ghost.
         * @return the direction of the ghost
         */
        public Direction getDirection() {
            return direction;
        }

        /**
         * Sets the direction of the ghost.
         * @param direction the direction to set
         */
        public void setDirection(Direction direction) {
            this.direction = direction;
        }

        /**
         * Checks if the ghost is in its initial move phase.
         * @return true if the ghost is in its initial move phase, false otherwise
         */
        public boolean isInitialMove() {
            return initialMove;
        }

        /**
         * Sets the initial move phase of the ghost.
         * @param initialMove the initial move phase to set
         */
        public void setInitialMove(boolean initialMove) {
            this.initialMove = initialMove;
        }

        /**
         * Checks if the ghost has been assigned an initial direction.
         * @return true if the ghost has been assigned an initial direction, false otherwise
         */
        public boolean isAssignedInitialDirection() {
            return assignedInitialDirection;
        }

        /**
         * Sets the assigned initial direction of the ghost.
         * @param assignedInitialDirection the assigned initial direction to set
         */
        public void setAssignedInitialDirection(boolean assignedInitialDirection) {
            this.assignedInitialDirection = assignedInitialDirection;
        }

        /**
         * Gets the move start time of the ghost.
         * @return the move start time of the ghost
         */
        public long getMoveStartTime() {
            return moveStartTime;
        }

        /**
         * Sets the move start time of the ghost.
         * @param moveStartTime the move start time to set
         */
        public void setMoveStartTime(long moveStartTime) {
            this.moveStartTime = moveStartTime;
        }

        /**
         * Gets the stop time of the ghost.
         * @return the stop time of the ghost
         */
        public long getStopTime() {
            return stopTime;
        }

        /**
         * Sets the stop time of the ghost.
         * @param stopTime the stop time to set
         */
        public void setStopTime(long stopTime) {
            this.stopTime = stopTime;
        }

        /**
         * Checks if the direction of the ghost has been set after stopping.
         * @return true if the direction has been set, false otherwise
         */
        public boolean isDirectionSetAfterStop() {
            return directionSetAfterStop;
        }

        /**
         * Sets the direction of the ghost after stopping.
         * @param directionSetAfterStop the direction to set
         */
        public void setDirectionSetAfterStop(boolean directionSetAfterStop) {
            this.directionSetAfterStop = directionSetAfterStop;
        }

        /**
         * Gets the starting X coordinate of the ghost.
         * @return the starting X coordinate of the ghost
         */
        public int getStartX() {
            return startX;
        }

        /**
         * Gets the starting Z coordinate of the ghost.
         * @return the starting Z coordinate of the ghost
         */
        public int getStartZ() {
            return startZ;
        }

        /**
         * Gets the type of the ghost.
         * @return the type of the ghost
         */
        public char getType() {
            return type;
        }
    }
}
