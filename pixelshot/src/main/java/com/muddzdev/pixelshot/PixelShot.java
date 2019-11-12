package com.muddzdev.pixelshot;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

/*
 * Copyright 2018 Muddi Walid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class PixelShot {

    private static final String TAG = PixelShot.class.getSimpleName();
    private static final String EXTENSION_JPG = ".jpg";
    private static final String EXTENSION_PNG = ".png";
    private static final String EXTENSION_NOMEDIA = ".nomedia";
    private static final int JPG_MAX_QUALITY = 100;

    private PixelShotListener listener;
    private int jpgQuality = JPG_MAX_QUALITY;
    private String fileExtension = EXTENSION_JPG;
    private String filename = String.valueOf(System.currentTimeMillis());
    private String path;
    private boolean saveInternal;
    private Bitmap bitmap;
    private View view;

    //TODO remove non-responsible code
    //TODO implement full javadoc


    private PixelShot(@NonNull View view) {
        this.view = view;
    }

    private PixelShot(@NonNull Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    public static PixelShot of(@NonNull View view) {
        return new PixelShot(view);
    }

    public static PixelShot of(@NonNull Bitmap bitmap) {
        return new PixelShot(bitmap);
    }

    /**
     * @param filename if not set, filename will default to a timestamp from {@link System#currentTimeMillis} at time of execution.
     */
    public PixelShot setFilename(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * For devices running Android Q/API 29 and higher, files will now be saved relative to the public storage of /storage/Pictures.
     * <p>Directories which don't already exist will be automatically created.</p>
     *
     * @param path if not set, path will default to /Pictures regardless of API level
     */
    public PixelShot setPath(String path) {
        this.path = path;
        return this;
    }

    /**
     * Only for devices running Android Q/API 29 and higher!
     *
     * @param path relative to the apps private storage
     */

    @RequiresApi(Build.VERSION_CODES.Q)
    public PixelShot setInternalPath(String path) {
        this.path = path;
        this.saveInternal = true;
        return this;
    }

    /**
     * Listen for successive or failure results when calling save()
     */
    public PixelShot setResultListener(PixelShotListener listener) {
        this.listener = listener;
        return this;
    }

    private void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    /**
     * Save as .jpg format in highest quality
     */
    public PixelShot toJPG() {
        jpgQuality = JPG_MAX_QUALITY;
        setFileExtension(EXTENSION_JPG);
        return this;
    }

    /**
     * Save as .jpg format in a custom quality between 0-100
     */
    public PixelShot toJPG(int jpgQuality) {
        this.jpgQuality = jpgQuality;
        setFileExtension(EXTENSION_JPG);
        return this;
    }

    /**
     * Save as .png format for lossless compression
     */
    public PixelShot toPNG() {
        setFileExtension(EXTENSION_PNG);
        return this;
    }

    /**
     * Save as .nomedia for making the picture invisible for photo viewer apps and galleries.
     */
    public PixelShot toNomedia() {
        setFileExtension(EXTENSION_NOMEDIA);
        return this;
    }

    private Context getAppContext() {
        if (view == null) {
            throw new NullPointerException("The provided View was null");
        } else {
            return view.getContext().getApplicationContext();
        }
    }

    private Bitmap getBitmap() {
        if (bitmap != null) {
            return bitmap;
        } else if (view instanceof TextureView) {
            bitmap = ((TextureView) view).getBitmap();
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            canvas.setBitmap(null);
            return bitmap;
        } else if (view instanceof RecyclerView) {
            bitmap = generateLongBitmap((RecyclerView) view);
            return bitmap;
        } else {
            bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            canvas.setBitmap(null);
            return bitmap;
        }
    }


    /**
     * @throws NullPointerException if View is null.
     */

    public void save() throws NullPointerException {
        if (view instanceof SurfaceView) {
            PixelCopyHelper.getSurfaceBitmap((SurfaceView) view, new PixelCopyHelper.PixelCopyListener() {
                @Override
                public void onSurfaceBitmapReady(Bitmap surfaceBitmap) {
                    new BitmapSaver(getAppContext(), surfaceBitmap, saveInternal, path, filename, fileExtension, jpgQuality, listener).execute();
                }

                @Override
                public void onSurfaceBitmapError() {
                    Log.d(TAG, "Couldn't create bitmap of the SurfaceView");
                    if (listener != null) {
                        listener.onPixelShotFailed();
                    }
                }
            });
        } else {
            new BitmapSaver(getAppContext(), getBitmap(), saveInternal, path, filename, fileExtension, jpgQuality, listener).execute();
        }
    }


    //TODO Is it the libraries responsibility to handle this?
    private Bitmap generateLongBitmap(RecyclerView recyclerView) {

        int itemCount = recyclerView.getAdapter().getItemCount();
        RecyclerView.ViewHolder viewHolder = recyclerView.getAdapter().createViewHolder(recyclerView, 0);

        //Measure the sizes of list item views to find out how big itemView should be
        viewHolder.itemView.measure(View.MeasureSpec.makeMeasureSpec(recyclerView.getWidth(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        // Define measured widths/heights
        int measuredItemHeight = viewHolder.itemView.getMeasuredHeight();
        int measuredItemWidth = viewHolder.itemView.getMeasuredWidth();

        //Set width/height of list item views
        viewHolder.itemView.layout(0, 0, measuredItemWidth, measuredItemHeight);

        //Create the Bitmap and Canvas to draw on
        Bitmap recyclerViewBitmap = Bitmap.createBitmap(recyclerView.getMeasuredWidth(), measuredItemHeight * itemCount, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(recyclerViewBitmap);

        //Draw RecyclerView Background:
        if (recyclerView.getBackground() != null) {
            Drawable drawable = recyclerView.getBackground().mutate();
            drawable.setBounds(measuredItemWidth, measuredItemHeight * itemCount, 0, 0);
            drawable.draw(canvas);
        }

        //Draw all list item views
        int viewHolderTopPadding = 0;
        for (int i = 0; i < itemCount; i++) {
            recyclerView.getAdapter().onBindViewHolder(viewHolder, i);
            viewHolder.itemView.setDrawingCacheEnabled(true);
            viewHolder.itemView.buildDrawingCache();
            canvas.drawBitmap(viewHolder.itemView.getDrawingCache(), 0f, viewHolderTopPadding, null);
            viewHolderTopPadding += measuredItemHeight;
            viewHolder.itemView.setDrawingCacheEnabled(false);
            viewHolder.itemView.destroyDrawingCache();

//            //TODO This should work but doesn't
//            recyclerView.getAdapter().onBindViewHolder(viewHolder, i);
//            viewHolder.itemView.draw(canvas);
//            canvas.drawBitmap(recyclerViewBitmap, 0f, viewHolderTopPadding, null);
//            viewHolderTopPadding += measuredItemHeight;
        }
        return recyclerViewBitmap;
    }

    public interface PixelShotListener {
        void onPixelShotSuccess(String path);

        void onPixelShotFailed();
    }

    static class BitmapSaver extends AsyncTask<Void, Void, Void> {

        private final WeakReference<Context> weakContext;
        private Handler handler = new Handler(Looper.getMainLooper());
        private PixelShotListener listener;
        private Bitmap bitmap;
        private String path;
        private boolean saveInternal;
        private String filename;
        private String fileExtension;
        private int jpgQuality;
        private File file;

        BitmapSaver(Context context, Bitmap bitmap, boolean saveInternal, String path, String filename, String fileExtension, int jpgQuality, PixelShotListener listener) {
            this.weakContext = new WeakReference<>(context);
            this.bitmap = bitmap;
            this.saveInternal = saveInternal;
            this.path = path;
            this.filename = filename;
            this.fileExtension = fileExtension;
            this.jpgQuality = jpgQuality;
            this.listener = listener;
        }

        private void cancelTask() {
            cancel(true);
            if (listener != null) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onPixelShotFailed();
                    }
                });
            }
        }

        private void saveLegacy() {
            if (path == null) {
                path = Environment.getExternalStorageDirectory() + File.separator + Environment.DIRECTORY_PICTURES;
            }
            File directory = new File(path);
            if (!directory.exists() && !directory.mkdirs()) {
                cancelTask();
            }
            file = new File(directory, filename + fileExtension);
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
                switch (fileExtension) {
                    case EXTENSION_JPG:
                        bitmap.compress(Bitmap.CompressFormat.JPEG, jpgQuality, out);
                        break;
                    case EXTENSION_PNG:
                        bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
                        break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                cancelTask();
            } finally {
                bitmap.recycle();
                bitmap = null;
            }
        }


        private void saveScoopedStorage() {
            String directory = Environment.DIRECTORY_PICTURES;
            if (path != null) {
                directory += File.separator + path;
            }
            ContentResolver resolver = weakContext.get().getContentResolver();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, Utils.getMimeType(fileExtension));
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, directory);
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (imageUri != null) {
                //TODO is there a better solution here?
                file = new File(directory, filename + fileExtension);
                try (OutputStream out = resolver.openOutputStream(imageUri)) {
                    switch (fileExtension) {
                        case EXTENSION_JPG:
                            bitmap.compress(Bitmap.CompressFormat.JPEG, jpgQuality, out);
                            break;
                        case EXTENSION_PNG:
                            bitmap.compress(Bitmap.CompressFormat.PNG, 0, out);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    cancelTask();
                    file = null;
                } finally {
                    bitmap.recycle();
                    bitmap = null;
                }
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            //Todo can this be improved?
            if (Utils.isAndroidQ() && !saveInternal) {
                saveScoopedStorage();
            } else {
                saveLegacy();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            weakContext.clear();

            //TODO rememeber to check file.exist()
            if (listener != null) {
                if (file != null) {
                    listener.onPixelShotSuccess(file.getAbsolutePath());
                } else {
                    listener.onPixelShotFailed();
                }
            }
        }
    }
}

