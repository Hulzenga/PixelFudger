package com.hulzenga.colorstripper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;

public class ColorStripperActivity extends Activity {

    private ImageView mImageView;

    private enum COLOR_COMPONENT {
        RED(16), GREEN(8), BLUE(0);

        private int shift;

        private COLOR_COMPONENT(int shift) {
            this.shift = shift;
        }
        
        public int getShift() {
            return shift;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_color_stripper);
        mImageView = (ImageView) findViewById(R.id.imageView);

        mImageView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub

            }
        });
        mImageView.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {
                // TODO Auto-generated method stub
                return false;
            }
        });
    }

    public void stripRed(View view) {
        strip(COLOR_COMPONENT.RED);
    }

    public void stripGreen(View view) {
        strip(COLOR_COMPONENT.GREEN);
    }

    public void stripBlue(View view) {
        strip(COLOR_COMPONENT.BLUE);
    }

    private Bitmap getBitmapFromImageView() {
        Drawable drwbl = mImageView.getDrawable();
        Bitmap bitmap = Bitmap.createBitmap(drwbl.getIntrinsicWidth(), drwbl.getIntrinsicHeight(), Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drwbl.draw(canvas);
        return bitmap;
    }

    private void strip(final COLOR_COMPONENT component) {

        final int STRIP_TIME = 1000;
        final int FRAME_TIME = 16;
        final int FRAMES = (int) Math.ceil(STRIP_TIME / (double) FRAME_TIME);

        Bitmap bitmap = getBitmapFromImageView();
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        //rows per frame
        final int rpf = (int) Math.ceil(height / (double) FRAMES);
        final int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, width, height);

        new Thread(new Runnable() {

            @Override
            public void run() {

                for (int i = 0; i < FRAMES; i++) {
                    for (int j = i * rpf * width; (j < (i + 1) * rpf * width) && (j < width * height); j++) {
                        pixels[j] &= ~(255 << component.getShift());
                    }
                    SystemClock.sleep(FRAME_TIME);
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mImageView.setImageBitmap(Bitmap.createBitmap(pixels, width, height, Config.ARGB_8888));
                        }
                    });
                }
            }
        }).start();

    }
}
