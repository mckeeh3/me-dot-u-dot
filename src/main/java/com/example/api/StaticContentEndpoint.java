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

  @Get("/css/index.css")
  public HttpResponse serveIndexCss() {
    return HttpResponses.staticResource("css/index.css");
  }

  @Get("/js/index.js")
  public HttpResponse serveIndexJs() {
    return HttpResponses.staticResource("js/index.js");
  }

  @Get("/playbook.html")
  public HttpResponse servePlaybookHtml() {
    return HttpResponses.staticResource("playbook.html");
  }

  @Get("/css/playbook.css")
  public HttpResponse servePlaybookCss() {
    return HttpResponses.staticResource("css/playbook.css");
  }

  @Get("/js/playbook.js")
  public HttpResponse servePlaybookJs() {
    return HttpResponses.staticResource("js/playbook.js");
  }

  @Get("/css/common.css")
  public HttpResponse serveCommonCss() {
    return HttpResponses.staticResource("css/common.css");
  }

  @Get("/js/common.js")
  public HttpResponse serveCommonJs() {
    return HttpResponses.staticResource("js/common.js");
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
