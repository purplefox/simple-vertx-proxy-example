package com.zanclus.simple.proxy.example;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.streams.Pump;
import io.vertx.ext.apex.Router;
import io.vertx.ext.apex.RoutingContext;

/**
 *
 * @author <a href="https://github.com/InfoSec812">Deven Phillips</a>
 */
public class Main extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOG.debug("Deploying Main verticle.");
        Vertx.vertx().deployVerticle("com.zanclus.simple.proxy.example.Main");
    }

    @Override
    public void start() throws Exception {
        Router router = Router.router(vertx);
        final HttpClient client = vertx.createHttpClient();
        router.route().handler((final RoutingContext ctx) -> {
            ctx.request().pause();
            LOG.error("Sending proxied request.");
            LOG.debug("C");
            final HttpClientRequest req = client.request(ctx.request().method(), 80, "www.reddit.com", ctx.request().uri());
            req.headers().clear().addAll(ctx.request().headers().remove("Host"));
            req.putHeader("Host", "www.reddit.com");
            LOG.debug("D");
            req.endHandler(end -> {
                req.end("\r\n");
            });
            LOG.debug("E");
//            req.setChunked(true);
//            LOG.debug("Chunked");
            req.handler(pResponse -> {
                pResponse.pause();
                LOG.error("Getting response from target");
                ctx.response().headers().clear().addAll(pResponse.headers());
                ctx.response().setStatusCode(pResponse.statusCode());
                ctx.response().setStatusMessage(pResponse.statusMessage());
                ctx.response().setChunked(true);
                ctx.response().bodyEndHandler(end -> {
                    ctx.response().end("\r\n");
                    ctx.response().close();
                });
                Pump targetToProxy = Pump.pump(pResponse, ctx.response());
                targetToProxy.start();
                pResponse.resume();
            });
            req.sendHead();
            LOG.debug("Getting pumped");
            Pump proxyToTarget = Pump.pump(ctx.request(), req);
            proxyToTarget.start();
            LOG.debug("Pumping away!");
            ctx.request().resume();
        });
        vertx.createHttpServer().requestHandler(router::accept).listen(8080);
    }
}