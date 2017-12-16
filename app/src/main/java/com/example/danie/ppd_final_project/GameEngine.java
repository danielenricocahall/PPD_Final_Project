package com.example.danie.ppd_final_project;


import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Stack;

/**
 * Created by danie on 11/24/2017.
 */

public class GameEngine extends SurfaceView implements Runnable, View.OnTouchListener {


    volatile boolean isPlaying = false;
    protected Thread gameThread;
    protected SurfaceHolder surfaceHolder;
    private static final int DESIRED_FPS = 30;
    private static final int TIME_BETWEEN_FRAMES = 1000 / DESIRED_FPS;
    private long previousTimeMillis;
    private long currentTimeMillis;
    private ArrayList<GameObject> gameObjects;
    public static int SCREEN_HEIGHT;
    public static int SCREEN_WIDTH;
    public static float DELTA_TIME;
    Paint paint;
    StationaryObject background_top, background_bottom;
    Dog dog;
    IndicatorShots indicatorShots;
    IndicatorDucks indicatorDucks;
    IndicatorScore indicatorScore;
    UserIndicator userIndicator;
    PauseButton pauseButton;
    boolean completedStartingSequence;
    public static int numberOfDucksPerStage;
    Stack<Duck> duckies = new Stack<>();
    DuckFactory duckFactory;
    boolean outOFBullets = false;
    static Context context;
    static int round;
    static boolean roundComplete;
    int maxPotentialRoundScore = 0;
    int roundScore = 0;
    int maxPotentialStageScore = 0;
    int stageScore = 0;
    boolean pauseButtonPressed = false;
    Stack<Float> deadDuckLandingSpots = new Stack<>();


    public GameEngine(Context context, int numberOfDucksPerStage, Point point, int round, int score) {
        super(context);
        this.context = context;
        this.round = round;
        roundComplete = false;
        surfaceHolder = getHolder();
        SCREEN_WIDTH = point.x;
        SCREEN_HEIGHT = point.y;
        paint = new Paint();

        gameObjects = new ArrayList<>();

        Camera.init(new Vector2D(point.x, point.y));

        background_top = new StationaryObject(
                R.drawable.background_top,
                SCREEN_WIDTH,
                (int)(SCREEN_HEIGHT * GameConstants.BACKGROUND_TOP_PERCENTAGE)
        );
        background_top.layer = GameConstants.MIDGROUND;
        gameObjects.add(background_top);

        background_bottom = new StationaryObject(
                R.drawable.background_bottom,
                SCREEN_WIDTH,
                (int)(SCREEN_HEIGHT * GameConstants.BACKGROUND_BOTTOM_PERCENTAGE)
        );
        background_bottom.layer = GameConstants.FOREGROUND;
        background_bottom.yPos = (int)(SCREEN_HEIGHT * (1 - GameConstants.BACKGROUND_BOTTOM_PERCENTAGE));
        gameObjects.add(background_bottom);

        dog = new Dog(new BasicPhysicsComponent());
        gameObjects.add(dog);

        duckFactory = new DuckFactory();

        userIndicator = new UserIndicator(round);
        gameObjects.add(userIndicator);

        indicatorShots = new IndicatorShots();
        gameObjects.add(indicatorShots);

        indicatorDucks = new IndicatorDucks();
        gameObjects.add(indicatorDucks);

        indicatorScore = new IndicatorScore();
        indicatorScore.setScore(score);
        gameObjects.add(indicatorScore);

        pauseButton = new PauseButton();
        gameObjects.add(pauseButton);

        this.setOnTouchListener(this);
        this.numberOfDucksPerStage = numberOfDucksPerStage;
        for(int ii = 0; ii < GameConstants.NUMBER_OF_DUCKS_DEPLOYED; ++ii)
        {
            duckies.push(duckFactory.makeRandomDuck());
        }
        GameSoundHandler.createSoundPool();
        GameSoundHandler.setContext(context);
        GameSoundHandler.loadSounds();
        completedStartingSequence = false;
        GameSoundHandler.playLongSound(GameConstants.STARTING_SEQUENCE_SOUND);
    }

    @Override
    public void run() {
        previousTimeMillis = System.currentTimeMillis();
        while (isPlaying) {
            handleGameLogic();
            if (!roundComplete) {
                update();
                draw();
                currentTimeMillis = System.currentTimeMillis();
                DELTA_TIME = (currentTimeMillis - previousTimeMillis) / 1000.0f;
                try {
                    gameThread.sleep(TIME_BETWEEN_FRAMES);
                } catch (InterruptedException e) {

                }
                previousTimeMillis = currentTimeMillis;
            } else {
                goToNextLevel();
                isPlaying = false;
            }
        }
    }

    public void handleGameLogic() {
        if (dog.position.y <= GameConstants.GROUND && dog.layer == GameConstants.BACKGROUND && !completedStartingSequence) {
            completedStartingSequence = true;
        }
        if (completedStartingSequence) {
            boolean readyToDeployMoreDucks = true;
            int duckIdx = 0;

            for (GameObject o : gameObjects) {
                if (o instanceof Duck) {
                    readyToDeployMoreDucks = false;
                }
            }

            if (readyToDeployMoreDucks) {
                handleEndOfStage();
                if (duckies.empty()) {
                    roundComplete = true;
                    userIndicator.gameOver();
                } else {
                    addMoreDucks();
                }
            }
        }
    }


    public void update() {
        for (Iterator<GameObject> iterator = gameObjects.iterator(); iterator.hasNext(); ) {
            GameObject gameObject = iterator.next();
            if (!gameObject.destroy) {
                gameObject.onUpdate();
            } else {
                iterator.remove();
            }
        }
    }

    public void draw() {
        if (surfaceHolder.getSurface().isValid()) {
            Canvas canvas = surfaceHolder.lockCanvas();
            canvas.drawARGB(255, 63, 191, 255);
            for(int ii = GameConstants.BACKGROUND; ii <= GameConstants.FOREGROUND; ++ii) {
                for (GameObject gameObject : gameObjects) {
                    if (gameObject.layer == ii) {
                        gameObject.onDraw(canvas);
                    }
                }
            }
            surfaceHolder.unlockCanvasAndPost(canvas);
        }
    }

    public void pause() {
        if (pauseButtonPressed) {
            pauseButton.paused = true;
            isPlaying = !isPlaying;
            GameSoundHandler.pauseAllSounds();
            GameSoundHandler.playSound(GameConstants.PAUSE_SOUND);
            draw();
        }
        try {
            gameThread.join();
        } catch (InterruptedException e) {
            Log.d("GameThread", "Error pausing!");
        }

    }


    public void resume() {
        pauseButton.paused = false;
        isPlaying = true;
        gameThread = new Thread(this);
        gameThread.start();
        GameSoundHandler.resumeAllSounds();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (Camera.worldRectToScreenRect(pauseButton.box).contains(event.getRawX(), event.getRawY())) {
                    if (isPlaying == true) {
                        pauseButtonPressed = true;
                        pause();
                    } else {
                        pauseButtonPressed = false;
                        resume();
                    }
                    break;
                }
                if (!pauseButton.paused) {
                    GameSoundHandler.playSound(GameConstants.GUN_SHOT_SOUND);
                    outOFBullets = indicatorShots.shoot();
                    for (GameObject o : gameObjects) {
                        if (o instanceof Duck) {
                            if(!((Duck) o).timeToFlyAway) {
                                Vector2D screenPos = Camera.worldToScreen(((Duck) o).position);
                                float delta_x = event.getRawX() - screenPos.x;
                                float delta_y = event.getRawY() - screenPos.y;
                                float distance = (float) Math.sqrt(delta_x * delta_x + delta_y * delta_y);
                                if (distance < 100.0f && ((Duck) o).isAlive) {
                                    shootDuck(((Duck) o));
                                }
                                else if(outOFBullets && ((Duck) o).isAlive){
                                    ((Duck) o).timeToFlyAway = true;
                                }
                            }
                        }
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
                break;

            case MotionEvent.ACTION_MOVE:
                break;

        }
        return false;
    }

    public void shootDuck(Duck duck)
    {
        duck.isAlive = false;
        deadDuckLandingSpots.push(duck.position.x);
        indicatorDucks.hitDuck(true);
        indicatorScore.addToScore(GameConstants.COLOR_TO_SCORE.get(duck.getDuckColor()));
        stageScore += GameConstants.COLOR_TO_SCORE.get(duck.getDuckColor());
        roundScore += stageScore;
    }

    public void addMoreDucks()
    {
        maxPotentialStageScore = 0;
        stageScore = 0;
        //the check below covers the case if you are doing 2 duck mode
        //and hit two ducks. The stack would still contain the position of the
        //first duck you hit.
        if (!deadDuckLandingSpots.empty()) {
            deadDuckLandingSpots.pop();
        }
        for (int ii = 0; ii < numberOfDucksPerStage; ++ii) {
            Duck duck = duckies.pop();
            duck.physicsComponent.setSpeed((round) * 0.5f * GameConstants.DUCK_SPEED);
            gameObjects.add(duck);
            indicatorShots.setNumShots(3);
            outOFBullets = false;
            maxPotentialStageScore += GameConstants.COLOR_TO_SCORE.get(duck.getDuckColor());
            maxPotentialRoundScore += maxPotentialStageScore;
        }
    }

    public void handleEndOfStage() {
        if (maxPotentialRoundScore != 0) {
            int numDucks;
            float popUpSpot;
            if (stageScore == maxPotentialStageScore) {
                numDucks = numberOfDucksPerStage;
                popUpSpot = deadDuckLandingSpots.pop();
            } else if (numberOfDucksPerStage == 2 && stageScore < maxPotentialStageScore && stageScore > 0) {
                numDucks = 1;
                popUpSpot = deadDuckLandingSpots.pop();
            } else {
                numDucks = 0;
                popUpSpot = 0.5f - Camera.screenXToWorldX(dog.current_sprite.getWidth());
            }

            for(int i = 0; i < (numberOfDucksPerStage - numDucks); i++){
                indicatorDucks.hitDuck(false);
            }

            dog.comeUpToFinishRound(numDucks, popUpSpot);
            draw();
            if (numDucks > 0) {
                GameSoundHandler.playSound(GameConstants.GOT_DUCK);
            } else {
                GameSoundHandler.playSound(GameConstants.DOG_LAUGH);
            }
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
            }
            dog.returnToGrass();
            draw();
        }

    }

    private int ducksRequiredToProgress()
    {
        if(round < 10)
        {
            return 6;
        }
        else if(round < 13)
        {
            return 7;
        }
        else if(round < 16)
        {
            return 8;
        }
        else if(round < 20)
        {
            return 9;
        }
        else
        {
            return 10;
        }

    }

    public void goToNextLevel() {
        if (ducksRequiredToProgress() < 1) {
            GameSoundHandler.playLongSound(GameConstants.ROUND_CLEAR);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }
            if (roundScore == maxPotentialRoundScore) {
                GameSoundHandler.playSound(GameConstants.PERFECT_SCORE);
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
            }
            GameSoundHandler.releaseResources();
            Intent i_start = new Intent(context, MainActivity.class);
            Bundle b = new Bundle();
            round++;
            b.putInt(GameConstants.ROUND, round);
            b.putInt(GameConstants.NUMBER_OF_DUCKS, numberOfDucksPerStage);
            b.putInt(GameConstants.SCORE, indicatorScore.getScore());
            i_start.putExtras(b);
            context.startActivity(i_start);
        } else {
            GameSoundHandler.playSound(GameConstants.GAME_OVER);
            try {
                Thread.sleep(4000);
            } catch (InterruptedException e) {
            }
            GameSoundHandler.releaseResources();
            Intent i_start = new Intent(context, StartupActivity.class);
            context.startActivity(i_start);
        }
    }
}
