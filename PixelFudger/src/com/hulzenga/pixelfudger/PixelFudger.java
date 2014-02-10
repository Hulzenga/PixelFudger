package com.hulzenga.pixelfudger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

public class PixelFudger extends Activity {

    private static final String TAG             = "PixelFudger";
    private static final int    PICTURE_REQUEST = 1;
    private static final String IMAGE_URI_KEY   = "uri";

    private static final int    STRIP_TIME      = 2000;
    private static final int    FRAME_TIME      = 32;
    private static final int    FRAMES          = (int) Math.ceil(STRIP_TIME / (double) FRAME_TIME);

    private List<View>          mControlls      = new ArrayList<View>();
    private ImageView           mImageView;
    private ProgressBar         mProgressBar;
    private Spinner             mStrategySpinner;
    private Button              mRedButton;
    private Button              mGreenButton;
    private Button              mBlueButton;

    private Bitmap              mBitmap;
    private Uri                 mBitmapUri;

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

    private interface PixelOperator {
        public int operate(int pixel, int shift);
    }

    private enum STRATEGY implements PixelOperator {
        DISCARD_CHANNEL("Discard Color Channel") {

            @Override
            public int operate(int pixel, int shift) {
                return pixel & (~(255 << shift));
            }

        },
        DISCARD_CHANNEL_AND_ALPHA("Discard Color Channel and Alpha") {

            @Override
            public int operate(int pixel, int shift) {

                int s = (pixel >> shift) & 255;

                int a = (pixel >> 24) & 255;
                int r = (pixel >> 16) & 255;
                int g = (pixel >> 8) & 255;
                int b = (pixel) & 255;

                int sum = r + g + b;
                if (sum == s) {
                    a = 0;
                } else {
                    a = a - (a * s) / (sum);
                }

                return Color.argb(a, r, g, b) & (~(255 << shift));
            }

        },
        LEVEL("Level Channel") {
            @Override
            public int operate(int pixel, int shift) {

                if (((pixel >> shift) & 255) < 127) {
                    return pixel & (~(255 << shift));
                } else {
                    return pixel | (255 << shift);
                }

            }
        },
        INVERT("Invert Channel") {
            @Override
            public int operate(int pixel, int shift) {
                return pixel ^ (255 << shift);
            }
        };

        private String representation;

        private STRATEGY(String representation) {
            this.representation = representation;
        }

        @Override
        public String toString() {
            return representation;
        }
    }

    private STRATEGY mStrategy = STRATEGY.DISCARD_CHANNEL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pixel_fudger);

        mImageView = (ImageView) findViewById(R.id.imageView);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mStrategySpinner = (Spinner) findViewById(R.id.strategySpinner);
        mRedButton = (Button) findViewById(R.id.redButton);
        mGreenButton = (Button) findViewById(R.id.greenButton);
        mBlueButton = (Button) findViewById(R.id.blueButton);

        mControlls.addAll(Arrays.asList(new View[] { mImageView, mStrategySpinner, mRedButton, mGreenButton,
                mBlueButton }));

        mImageView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, PICTURE_REQUEST);
            }
        });
        mImageView.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View v) {

                File path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String filename = "PixelFudger_" + timestamp + ".jpg";
                File file = new File(path, filename);
                FileOutputStream stream;
                try {
                    stream = new FileOutputStream(file);
                    mBitmap.compress(CompressFormat.JPEG, 100, stream);
                    stream.close();
                    
                    Uri uri = Uri.fromFile(file);                    
                    Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    intent.setData(uri);
                    sendBroadcast(intent);
                    
                    Toast.makeText(getApplicationContext(), "Saved Image", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Log.e(TAG, "saveAndShare (compressing):", e);
                    Toast.makeText(getApplicationContext(), "Failed to save image", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
        });

        ArrayAdapter<STRATEGY> adapter = new ArrayAdapter<STRATEGY>(this,
                android.R.layout.simple_spinner_dropdown_item, STRATEGY.values());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_item);
        mStrategySpinner.setAdapter(adapter);
        mStrategySpinner.setOnItemSelectedListener(new OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mStrategy = STRATEGY.values()[position];
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        boolean loadFromSavedState = false;
        if (savedInstanceState != null) {
            Uri imageUri = (Uri) savedInstanceState.getParcelable(IMAGE_URI_KEY);
            if (imageUri != null) {
                new LoadBitmap().execute(imageUri);
                loadFromSavedState = true;
            }
        }
        
        if (!loadFromSavedState) {
            mBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.angrave).copy(Config.ARGB_8888, true);
            mImageView.setImageBitmap(mBitmap);
        }        
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(IMAGE_URI_KEY, mBitmapUri);
    }

    private void lockControlls() {
        for (View view : mControlls) {
            view.setEnabled(false);
            view.setClickable(false);
        }
    }

    private void releaseControlls() {
        for (View view : mControlls) {
            view.setEnabled(true);
            view.setClickable(true);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICTURE_REQUEST) {
            if (resultCode == RESULT_OK) {
                new LoadBitmap().execute(data.getData());
            }
        }
    }
    
    private class LoadBitmap extends AsyncTask<Uri, Integer, Boolean> {

        @Override
        protected void onPreExecute() {
            mImageView.setVisibility(View.INVISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Boolean doInBackground(Uri... uris) {
            try {
                InputStream stream = getContentResolver().openInputStream(uris[0]);

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(stream, null, options);
                stream.close();

                int w = options.outWidth;
                int h = options.outHeight;
                int displayW = getResources().getDisplayMetrics().widthPixels;
                int displayH = getResources().getDisplayMetrics().heightPixels;

                int sample = 1;
                while (w > displayW * sample || h > displayH * sample) {
                    sample = sample * 2;
                }

                options.inJustDecodeBounds = false;
                options.inSampleSize = sample;

                stream = getContentResolver().openInputStream(uris[0]);
                Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
                stream.close();

                if (mBitmap != null) {
                    mBitmap.recycle();
                }
                mBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                
                Canvas c = new Canvas(mBitmap);
                c.drawBitmap(bitmap, 0, 0, null);
                bitmap.recycle();
                mBitmapUri = uris[0];
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to loadBitmap: " + e.getMessage());
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mImageView.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.INVISIBLE);
            if (result) {
                mImageView.setImageBitmap(mBitmap);
            }
        }
    }

    public void onClickRed(View view) {
        fudge(COLOR_COMPONENT.RED);
    }

    public void onClickGreen(View view) {
        fudge(COLOR_COMPONENT.GREEN);
    }

    public void onClickBlue(View view) {
        fudge(COLOR_COMPONENT.BLUE);
    }

    private void fudge(final COLOR_COMPONENT component) {
        lockControlls();

        final int width = mBitmap.getWidth();
        final int height = mBitmap.getHeight();
        // rows per frame
        final int rpf = (int) Math.ceil(height / (double) FRAMES);
        final int[] pixels = new int[width * height];
        mBitmap.getPixels(pixels, 0, mBitmap.getWidth(), 0, 0, width, height);

        new Thread(new Runnable() {

            @Override
            public void run() {

                try {
                    int shift = component.getShift();
                    int lastRow = 0;
                    long lastFrameTime = SystemClock.elapsedRealtime(), elapsed;
                    for (int row = 0; row < height; row++) {

                        for (int i = row * width; i < (row + 1) * width; i++) {
                            pixels[i] = mStrategy.operate(pixels[i], shift);
                        }

                        if ((row + 1) % rpf == 0 || (row + 1) == height) {
                            elapsed = SystemClock.elapsedRealtime() - lastFrameTime;
                            if (elapsed < FRAME_TIME) {
                                SystemClock.sleep(FRAME_TIME - elapsed);
                            }
                            mBitmap.setPixels(pixels, lastRow * width, width, 0, lastRow, width, (row + 1) - lastRow);
                            lastRow = (row + 1);

                            lastFrameTime = SystemClock.elapsedRealtime();

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mImageView.setImageBitmap(mBitmap);
                                }
                            });
                        }
                    }
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            releaseControlls();
                        }
                    });
                }
            }
        }).start();
    }
}
