package com.mindorks.tensorflowexample;

import android.os.AsyncTask;
import java.io.*;
import java.net.*;

/**
 * Created by pavelr on 1/3/18.
 */

public class RequestSender extends AsyncTask<String, String, String> {

    public RequestSender(){
        //set context variables if required
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
    }

    @Override
    protected String doInBackground(String... params) {

        String urlString = params[0]; // URL to call

        String data = params[1]; //data to post

        Post(urlString, data);

        System.out.println("Message sent successfully");
        return "";
    }

    private void Post(String urlString, String data) {
        HttpURLConnection urlConnection = null;

        try {
            URL url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");
            urlConnection.setDoOutput(true);

            BufferedOutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            BufferedWriter writer = new BufferedWriter (new OutputStreamWriter(out, "UTF-8"));

            writer.write(data);

            writer.flush();
            writer.close();
            out.close();
            urlConnection.connect();

        } catch (Exception e) {
            System.out.println(e.getMessage());
        } finally {
            if (urlConnection != null) {
                urlConnection.disconnect();
            }
        }
    }
}
