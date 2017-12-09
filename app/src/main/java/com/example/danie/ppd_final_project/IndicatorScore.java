package com.example.danie.ppd_final_project;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/**
 * Created by Brian on 12/9/2017.
 */

public class IndicatorScore extends GameObject {

    protected int score;
    protected Vector2D scoreLocation;

    protected Paint paint;

    public IndicatorScore(Context context) {

        score = 0;

        scoreLocation = new Vector2D(
                GameEngine.SCREEN_WIDTH * 0.91f,
                GameEngine.SCREEN_HEIGHT * 0.94f
        );

        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setTextSize(50);
        paint.setTextAlign(Paint.Align.RIGHT);

    }

    @Override
    public void init() {

    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawText(Integer.toString(score), scoreLocation.x, scoreLocation.y, paint);
        canvas.drawText("SCORE", scoreLocation.x, scoreLocation.y + GameEngine.SCREEN_HEIGHT * 0.04f, paint);
    }

    @Override
    public void onUpdate() {

    }

    public void setScore(int newScore) {
        score = newScore;
    }

    public int getScore() {
        return score;
    }

    public void addToScore(int points) {
        score += points;
    }

}