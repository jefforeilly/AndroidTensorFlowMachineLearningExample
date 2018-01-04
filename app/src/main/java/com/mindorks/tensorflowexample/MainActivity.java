/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.mindorks.tensorflowexample;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;
import android.widget.ListView;
import android.widget.EditText;
import android.text.InputType;
import android.widget.LinearLayout;

import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String LABEL_FILE =
            "file:///android_asset/imagenet_comp_graph_label_strings.txt";
    private static final String DEFAULT_TARGET_URL = "http://192.168.50.100:8070/tunnel/192.168.50.100:45000/";

    private Classifier classifier;
    private Executor executor = Executors.newSingleThreadExecutor();
    private ImageButton btnDetectObject;
    private CameraView cameraView;

    private class ObjectDescription {
        private String name;
        private String confidence;

        public ObjectDescription(String name, String confidence) {
            this.name = name;
            this.confidence = confidence;
        }

        public String getName() {
            return this.name;
        }

        public String getConfidence() {
            return this.confidence;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        cameraView = (CameraView) findViewById(R.id.cameraView);
        btnDetectObject = (ImageButton) findViewById(R.id.btnDetectObject);

        // An extended camera listener to pass the parent view inside
        class ExtendedCameraListener extends CameraListener {
            private MainActivity parentView;

            public ExtendedCameraListener(MainActivity parentView) {
                this.parentView = parentView;
            }

            public MainActivity getParentView() {
                return parentView;
            }
        }

        cameraView.setCameraListener(new ExtendedCameraListener(this) {

            @Override
            public void onPictureTaken(byte[] picture) {
                super.onPictureTaken(picture);

                Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);

                bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

                final List<Classifier.Recognition> results = classifier.recognizeImage(bitmap);

                getParentView().showAlertDialog(results, picture);
            }
        });

        btnDetectObject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.captureImage();
            }
        });

        initTensorFlowAndLoadModel();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                classifier.close();
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_FILE,
                            LABEL_FILE,
                            INPUT_SIZE,
                            IMAGE_MEAN,
                            IMAGE_STD,
                            INPUT_NAME,
                            OUTPUT_NAME);
                    makeButtonVisible();
                } catch (final Exception e) {
                    throw new RuntimeException("Error initializing TensorFlow!", e);
                }
            }
        });
    }

    private void makeButtonVisible() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                btnDetectObject.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showAlertDialog(final List<Classifier.Recognition> items, final byte[] picture) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        // Setup user choice
        final EditText userInputChoice = new EditText(this);
        userInputChoice.setHint("If no other options match the picture");
        userInputChoice.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(userInputChoice);

        // Set up the input
        final EditText inputUrl = new EditText(this);
        inputUrl.setText(MainActivity.DEFAULT_TARGET_URL);
        inputUrl.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(inputUrl);

        builder.setView(layout);


        builder.setSingleChoiceItems(getItemsToShow(items), 0, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {}
        });
        builder.setTitle("Choose correct option and enter url to send to:");

        // Set the neutral/cancel button click listener
        builder.setNeutralButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {}
        });

        // Set the positive/yes button click listener
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String chosenDescription = null;

                // Get data from dialog
                ListView lw = ((AlertDialog)dialog).getListView();
                if (lw.getAdapter().getCount() > 0) {
                    CharSequence checkedItem = (CharSequence)lw.getAdapter().getItem(lw.getCheckedItemPosition());
                    chosenDescription = (String)checkedItem;
                }

                String urlString = inputUrl.getText().toString();
                if (chosenDescription == null && TextUtils.isEmpty(userInputChoice.getText())){
                    userInputChoice.setError("Please set this, as no other choice is given");
                }

                String inputChoice = userInputChoice.getText().toString();
                if (!inputChoice.equals("")) {
                    chosenDescription = inputChoice;
                }

                Toast.makeText(getApplicationContext(), "\"" +
                        chosenDescription + "\"" + " chosen. " +
                                "Sending choice to server: " + urlString, Toast.LENGTH_LONG).show();

                String[] splitDescription = chosenDescription.split("_");
                ObjectDescription objectDescription;
                if (splitDescription.length > 1) {
                    objectDescription = new ObjectDescription(splitDescription[0], splitDescription[1]);
                } else {
                    objectDescription = new ObjectDescription(splitDescription[0], "(100%)");
                }

                // send request
                sendRequest(urlString, objectDescription, picture);
            }
        });

        AlertDialog dialog = builder.create();

        // Display the alert dialog on interface
        dialog.show();
    }

    private void sendRequest(String urlString, final ObjectDescription objDesc, final byte[] picture) {
        // Instantiate the RequestQueue.
        RequestQueue queue = Volley.newRequestQueue(MainActivity.this);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, urlString,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Display the response string.
                        System.out.println("Successful");
                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                System.out.println("That didn't work! " + error);
            }
        }) {
            @Override
            public byte[] getBody() throws AuthFailureError {
                return picture;
            }

            @Override
            public Map<String, String> getHeaders() {
                Map<String, String>  params = new HashMap<>();
                params.put("name", objDesc.getName().replace(" ", "_"));

                return params;
            }
        };

        // Add the request to the RequestQueue.
        queue.add(stringRequest);
    }

    private CharSequence[] getItemsToShow(List<Classifier.Recognition> items) {
        CharSequence[] itemsToShow = new CharSequence[items.size()];

        int i = 0;
        for (Classifier.Recognition item : items) {
            itemsToShow[i] = item.getTitle();
            i++;
        }

        return itemsToShow;
    }
}
