package xyz.sadiulhakim.restClient;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

public class RestClientTest {

    public static void main(String[] args) {

        try {
            var client = RestClient.create();

            String body = client.get()
                    .uri("https://jsonplaceholder.typicode.com/users/1")
                    .accept(MediaType.APPLICATION_JSON)
                    .acceptCharset(StandardCharsets.UTF_8)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw new HttpClientErrorException(res.getStatusCode(), "Client Error");
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new HttpServerErrorException(res.getStatusCode(), "Server Error");
                    })
                    .body(String.class);

            System.out.println(body);

            Post body1 = client.get()
                    .uri("https://jsonplaceholder.typicode.com/posts/{id}", 1)
                    .retrieve()
                    .body(Post.class);
            System.out.println(body1);

        } catch (HttpClientErrorException | HttpServerErrorException ex) {
            System.out.println(ex.getMessage());
        }
    }
}

record Post(
        int id,
        String title,
        String body
) {
}