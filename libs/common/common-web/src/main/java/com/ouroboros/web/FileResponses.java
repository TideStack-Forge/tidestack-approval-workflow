package com.ouroboros.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerResponse;

import com.ouroboros.util.FileResult;

/** Synchronous file responses share one ownership boundary for MVC and functional routes. */
public final class FileResponses {
  private FileResponses() { }

  public static ServerResponse response(FileResult file) {
    return ServerResponse.ok().build((request, response) -> {
      write(file, response);
      return null;
    });
  }

  public static void write(FileResult file, HttpServletResponse response) throws IOException {
    try (file) {
      headers(file).forEach((name, values) -> values.forEach(value -> response.setHeader(name, value)));
      try (var input = file.openStream()) { input.transferTo(response.getOutputStream()); }
    }
  }

  private static HttpHeaders headers(FileResult file) {
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType(file.mediaType()));
    headers.setContentLength(file.length());
    headers.setContentDisposition(ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build());
    return headers;
  }
}
