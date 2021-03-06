package org.tomahawk.libtomahawk.utils;

import com.google.common.collect.Multimap;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.tomahawk.libtomahawk.collection.Album;
import org.tomahawk.libtomahawk.collection.Artist;
import org.tomahawk.libtomahawk.collection.Image;
import org.tomahawk.libtomahawk.collection.Track;
import org.tomahawk.libtomahawk.resolver.Query;
import org.tomahawk.libtomahawk.resolver.Result;
import org.tomahawk.tomahawk_android.R;
import org.tomahawk.tomahawk_android.adapters.TomahawkBaseAdapter;

import android.content.Context;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.widget.ImageView;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class TomahawkUtils {

    public static String TAG = TomahawkUtils.class.getName();

    /**
     * Author: Chas Emerick (source: http://mrfoo.de/archiv/1176-Levenshtein-Distance-in-Java.html)
     *
     * This method uses the LevenstheinDistance algorithm to compute the similarity of two strings.
     *
     * @return the minimum number of single-character edits required to change one of the given
     * strings into the other
     */
    public static int getLevenshteinDistance(String s, String t) {
        if (s == null || t == null) {
            throw new IllegalArgumentException("Strings must not be null");
        }
        if (TextUtils.isEmpty(s)) {
            return t.length();
        } else if (TextUtils.isEmpty(t)) {
            return s.length();
        }
        int n = s.length(); // length of s
        int m = t.length(); // length of t

        if (n == 0) {
            return m;
        } else if (m == 0) {
            return n;
        }

        int p[] = new int[n + 1]; //'previous' cost array, horizontally
        int d[] = new int[n + 1]; // cost array, horizontally
        int _d[]; //placeholder to assist in swapping p and d

        // indexes into strings s and t
        int i; // iterates through s
        int j; // iterates through t

        char t_j; // jth character of t

        int cost; // cost

        for (i = 0; i <= n; i++) {
            p[i] = i;
        }

        for (j = 1; j <= m; j++) {
            t_j = t.charAt(j - 1);
            d[0] = j;

            for (i = 1; i <= n; i++) {
                cost = s.charAt(i - 1) == t_j ? 0 : 1;
                // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
                d[i] = Math.min(Math.min(d[i - 1] + 1, p[i] + 1), p[i - 1] + cost);
            }

            // copy current distance counts to 'previous row' distance counts
            _d = p;
            p = d;
            d = _d;
        }

        // our last action in the above loop was to switch d and p, so p now
        // actually has the most recent cost counts
        return p[n];
    }

    /**
     * This method converts dp unit to equivalent device specific value in pixels.
     *
     * @param dp      A value in dp(Device independent pixels) unit. Which we need to convert into
     *                pixels
     * @param context Context to get resources and device specific display metrics
     * @return A float value to represent Pixels equivalent to dp according to device
     */
    public static int convertDpToPixel(int dp, Context context) {
        Resources resources = context.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        return (int) (dp * (metrics.densityDpi / 160f));
    }

    /**
     * Converts a track duration int into the proper String format
     *
     * @param duration the track's duration
     * @return the formated string
     */
    public static String durationToString(long duration) {
        return String.format("%02d", (duration / 60000)) + ":" + String
                .format("%02.0f", (double) (duration / 1000) % 60);
    }

    /**
     * Parse a given String into a Date.
     */
    public static Date stringToDate(String rawDate) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        Date date = null;
        try {
            date = dateFormat.parse(rawDate);
        } catch (ParseException e) {
            Log.e(TAG, "stringToDate: " + e.getClass() + ": " + e.getLocalizedMessage());
        }
        return date;
    }

    private static String getCacheKey(String... strings) {
        String result = "";
        for (String s : strings) {
            result += "\t\t" + s.toLowerCase();
        }
        return result;
    }

    public static String getCacheKey(TomahawkBaseAdapter.TomahawkListItem tomahawkListItem) {
        if (tomahawkListItem instanceof Artist) {
            return getCacheKey(tomahawkListItem.getName());
        } else if (tomahawkListItem instanceof Album) {
            return getCacheKey(tomahawkListItem.getName(), tomahawkListItem.getArtist().getName());
        } else if (tomahawkListItem instanceof Track) {
            return getCacheKey(tomahawkListItem.getName(), tomahawkListItem.getAlbum().getName(),
                    tomahawkListItem.getArtist().getName());
        } else if (tomahawkListItem instanceof Query) {
            Query query = ((Query) tomahawkListItem);
            boolean isFullTextQuery = query.isFullTextQuery();
            if (isFullTextQuery) {
                return getCacheKey(query.getFullTextQuery());
            } else {
                return getCacheKey(query.getName(), query.getAlbum().getName(),
                        query.getArtist().getName(), query.getResultHint());
            }
        }
        return "";
    }

    public static String getCacheKey(Image image) {
        return getCacheKey(image.getImagePath());
    }

    public static String getCacheKey(Result result) {
        return getCacheKey(result.getPath());
    }

    public static String httpsPost(String urlString, Multimap<String, String> params)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        return httpsPost(urlString, paramsListToString(params), false);
    }

    private static String httpsPost(String urlString, String paramsString,
            boolean contentTypeIsJson)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        // Create the SSL connection
        SSLContext sc;
        sc = SSLContext.getInstance("TLS");
        sc.init(null, null, new java.security.SecureRandom());
        connection.setSSLSocketFactory(sc.getSocketFactory());

        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(paramsString.getBytes().length);
        if (contentTypeIsJson) {
            connection.setRequestProperty("Content-type", "application/json; charset=utf-8");
        } else {
            connection.setRequestProperty("Content-type", "application/x-www-form-urlencoded");
        }
        connection.setRequestProperty("Accept", "application/json; charset=utf-8");
        OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream());
        out.write(paramsString);
        out.close();
        String output = inputStreamToString(connection);
        connection.disconnect();
        return output;
    }

    public static String httpsGet(String urlString)
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        // Create the SSL connection
        SSLContext sc;
        sc = SSLContext.getInstance("TLS");
        sc.init(null, null, new java.security.SecureRandom());
        connection.setSSLSocketFactory(sc.getSocketFactory());

        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        connection.setRequestProperty("Accept", "application/json; charset=utf-8");
        connection.setRequestProperty("Content-type", "application/json; charset=utf-8");
        String output = inputStreamToString(connection);
        connection.disconnect();
        return output;
    }

    public static boolean httpHeaderRequest(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept-Encoding", "");
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == HttpURLConnection.HTTP_OK;
        } catch (MalformedURLException e) {
            Log.e(TAG, "httpHeaderRequest: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (ProtocolException e) {
            Log.e(TAG, "httpHeaderRequest: " + e.getClass() + ": " + e.getLocalizedMessage());
        } catch (IOException e) {
            Log.e(TAG, "httpHeaderRequest: " + e.getClass() + ": " + e.getLocalizedMessage());
        }
        return false;
    }

    public static String paramsListToString(Multimap<String, String> params)
            throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;

        for (String key : params.keySet()) {
            Collection<String> values = params.get(key);
            for (String value : values) {
                if (first) {
                    first = false;
                } else {
                    result.append("&");
                }
                result.append(URLEncoder.encode(key, "UTF-8"));
                result.append("=");
                result.append(URLEncoder.encode(value, "UTF-8"));
            }
        }

        return result.toString();
    }

    private static String inputStreamToString(HttpURLConnection connection) throws IOException {
        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        } catch (FileNotFoundException e) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            return response.toString();
        }
    }

    /**
     * Load a {@link android.graphics.Bitmap} asynchronously
     *
     * @param context   the context needed for fetching resources
     * @param imageView the {@link android.widget.ImageView}, which will be used to show the {@link
     *                  android.graphics.Bitmap}
     * @param query     the query to get the albumart/artist image's path to load the image from
     * @param width     the width in density independent pixels to scale the image down to
     */
    public static void loadImageIntoImageView(Context context, ImageView imageView, Query query,
            int width) {
        Image image = null;
        if (query != null) {
            if (query.getArtist() != null) {
                image = query.getAlbum().getImage();
            }
            if (image == null && query.getArtist() != null) {
                image = query.getArtist().getImage();
            }
        }
        loadImageIntoImageView(context, imageView, image, width);
    }

    /**
     * Load a {@link android.graphics.Bitmap} asynchronously
     *
     * @param context   the context needed for fetching resources
     * @param imageView the {@link android.widget.ImageView}, which will be used to show the {@link
     *                  android.graphics.Bitmap}
     * @param image     the path to load the image from
     * @param width     the width in density independent pixels to scale the image down to
     */
    public static void loadImageIntoImageView(Context context, ImageView imageView, Image image,
            int width) {
        if (image != null && !TextUtils.isEmpty(image.getImagePath())) {
            String imagePath = buildImagePath(context, image, width);
            Picasso.with(context).load(TomahawkUtils.preparePathForPicasso(imagePath))
                    .placeholder(R.drawable.no_album_art_placeholder)
                    .error(R.drawable.no_album_art_placeholder).into(imageView);
        } else {
            Picasso.with(context).load(R.drawable.no_album_art_placeholder)
                    .placeholder(R.drawable.no_album_art_placeholder)
                    .error(R.drawable.no_album_art_placeholder).into(imageView);
        }
    }

    /**
     * Load a {@link android.graphics.Bitmap} asynchronously
     *
     * @param context the context needed for fetching resources
     * @param image   the path to load the image from
     * @param target  the Target which the loaded image will be pushed to
     * @param width   the width in density independent pixels to scale the image down to
     */
    public static void loadImageIntoBitmap(Context context, Image image, Target target, int width) {
        if (image != null && !TextUtils.isEmpty(image.getImagePath())) {
            String imagePath = buildImagePath(context, image, width);
            Picasso.with(context).load(TomahawkUtils.preparePathForPicasso(imagePath))
                    .placeholder(R.drawable.no_album_art_placeholder)
                    .error(R.drawable.no_album_art_placeholder).into(target);
        } else {
            Picasso.with(context).load(R.drawable.no_album_art_placeholder)
                    .placeholder(R.drawable.no_album_art_placeholder)
                    .error(R.drawable.no_album_art_placeholder).into(target);
        }
    }

    public static String preparePathForPicasso(String path) {
        if (TextUtils.isEmpty(path) || path.contains("https://") || path.contains("http://")) {
            return path;
        }
        return "file:" + path;
    }

    private static String buildImagePath(Context context, Image image, int width) {
        ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if (image.isHatchetImage()) {
            int squareImageWidth = Math.min(image.getHeight(), image.getWidth());
            width = convertDpToPixel(width, context);
            if (connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI) != null
                    && connMgr.getNetworkInfo(ConnectivityManager.TYPE_WIFI).isConnected()) {
                if (squareImageWidth > width) {
                    return image.getImagePath() + "?width=" + width;
                }
            } else if (squareImageWidth > width / 2) {
                return image.getImagePath() + "?width=" + width / 2;
            }
        }
        return image.getImagePath();
    }
}
