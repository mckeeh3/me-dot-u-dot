package com.example.api;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.http.HttpResponses;

/**
 * Endpoint to serve static content like HTML, CSS, and JavaScript files.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint
public class StaticContentEndpoint {

  @Get("/")
  public HttpResponse serveIndex() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("/index.html")
  public HttpResponse serveIndexHtml() {
    return HttpResponses.staticResource("index.html");
  }

  @Get("/index.js")
  public HttpResponse serveIndexJs() {
    return HttpResponses.staticResource("index.js");
  }

  @Get("/playbook.html")
  public HttpResponse servePlaybookHtml() {
    return HttpResponses.staticResource("playbook.html");
  }

  @Get("/playbook.js")
  public HttpResponse servePlaybookJs() {
    return HttpResponses.staticResource("playbook.js");
  }

  @Get("/favicon.ico")
  public HttpResponse serveFavicon() {
    return HttpResponses.staticResource("favicon.ico");
  }

  @Get("/sounds/**")
  public HttpResponse serveSounds(HttpRequest request) {
    return HttpResponses.staticResource(request, "/sounds/");
  }

  @Get("/images/**")
  public HttpResponse serveImages(HttpRequest request) {
    return HttpResponses.staticResource(request, "/images/");
  }
}
