package xyz.gapeac.sxyforgemod;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.io.*;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class Utils
{
    public static Mono<String> GetURL(String url)
    {
        return GetURL(url, null);
    }

    public static Mono<String> GetURL(String url, List<Tuple2<String, String>> headerProperties)
    {
        return Mono.create(callback ->
        {
            HttpURLConnection connection = null;

            try
            {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestMethod("GET");

                if (headerProperties != null)
                {
                    for (Tuple2<String, String> str : headerProperties)
                    {
                        connection.setRequestProperty(str.getT1(), str.getT2());
                    }
                }

                int statusCode = connection.getResponseCode();

                if (statusCode != HttpURLConnection.HTTP_OK && statusCode != HttpURLConnection.HTTP_CREATED)
                {
                    String error = String.format("HTTP Code: '%1$s' from '%2$s'", statusCode, url);
                    callback.error(new ConnectException(error));
                }

                char[] buffer = new char[1024 * 4];
                int n;

                InputStream stream       = new BufferedInputStream(connection.getInputStream());
                InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
                StringWriter writer      = new StringWriter();

                while (-1 != (n = reader.read(buffer)))
                {
                    writer.write(buffer, 0, n);
                }

                callback.success(writer.toString());
            }
            catch (Exception e)
            {
                SxyForgeMod.LOGGER.error("Failed to get URL " + url);
                SxyForgeMod.LOGGER.error(e.getMessage());

                callback.error(e);
            }
            finally
            {
                if (connection != null)
                {
                    connection.disconnect();
                }
            }
        });
    }
}
