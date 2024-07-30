package com.gt.matlab.engine.http;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mathworks.engine.MatlabEngine;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class EngineServer {

    private HttpServer server;
    private final Gson gson = new Gson();

    public static void main(String[] args) throws IOException {
        EngineServer engineServer = new EngineServer();
        engineServer.start(8080);
    }

    public void start(int port) throws IOException {

        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/feval", this::handleFeval);
        server.createContext("/eval", this::handleEval);
        server.createContext("/getVariable", this::handleGetVariable);
        server.createContext("/putVariable", this::handlePutVariable);

        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    private void notImplemented(HttpExchange exchange) throws IOException {
        String response = "Not implemented";
        exchange.sendResponseHeaders(200, response.length());
        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().close();
    }

    private void handlePutVariable(HttpExchange exchange) throws IOException {
        withMatlab(exchange, (engine) -> {
            PutVariableArgs args = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), PutVariableArgs.class);
            engine.putVariable(args.varName, args.varData);
            return gson.toJson(new GetVariableResponse(
                    args.varName,
                    args.varData
            ));
        });
    }

    private void handleGetVariable(HttpExchange exchange) throws IOException {
        withMatlab(exchange, (engine) -> {
            GetVariableArgs args = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), GetVariableArgs.class);
            Object value = engine.getVariable(args.varName);
            return gson.toJson(new GetVariableResponse(
                    args.varName,
                    value
            ));
        });
    }

    private void handleEval(HttpExchange exchange) throws IOException {
        withMatlab(exchange, (engine) -> {
            StringWriter stdoutWriter = new StringWriter();
            StringWriter stderrWriter = new StringWriter();
            EvalArgs args = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), EvalArgs.class);
            engine.eval(args.command, stdoutWriter, stderrWriter);
            return gson.toJson(new EvalResponse(
                    args,
                    stdoutWriter.toString(),
                    stderrWriter.toString()
            ));
        });
    }

    private void handleFeval(HttpExchange exchange) throws IOException {
        withMatlab(exchange, (engine) -> {
            StringWriter stdoutWriter = new StringWriter();
            StringWriter stderrWriter = new StringWriter();
            FevalArgs args = gson.fromJson(new InputStreamReader(exchange.getRequestBody()), FevalArgs.class);
            Object result = engine.feval(args.nlhs, args.func, stdoutWriter, stderrWriter, args.args);
            return gson.toJson(new FevalResponse(
                    args,
                    result,
                    stdoutWriter.toString(),
                    stderrWriter.toString()
            ));
        });
    }

    interface ThrowingFunction<T, R> {

        R apply(T t) throws Exception;

    }

    private void withMatlab(HttpExchange exchange, ThrowingFunction<MatlabEngine, String> bloc) throws IOException {
        MatlabEngine engine = null;
        try {
            engine = MatlabEngine.getCurrentMatlab();

            String response = bloc.apply(engine);

            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        } catch (JsonSyntaxException | JsonIOException e) {
            String message = "Request body cannot be properly deserialized";
            exchange.sendResponseHeaders(400, message.length());
            exchange.getResponseBody().write(message.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            exchange.sendResponseHeaders(503, e.getMessage().length());
            exchange.getResponseBody().write(e.getMessage().getBytes(StandardCharsets.UTF_8));
        } finally {
            exchange.getResponseBody().close();
            if (engine != null) {
                engine.disconnectAsync();
            }
        }

    }

    static class PutVariableArgs {
        public String varName;
        public Object varData;
    }

    static class GetVariableArgs {
        public String varName;
    }

    static class GetVariableResponse {
        public String varName;
        public Object varData;

        public GetVariableResponse(String varName, Object varData) {
            this.varName = varName;
            this.varData = varData;
        }
    }

    static class EvalArgs {
        public String command;
    }

    static class EvalResponse {
        public EvalArgs incomingArgs;
        public String stdout;
        public String stderr;

        public EvalResponse(EvalArgs incomingArgs, String stdout, String stderr) {
            this.incomingArgs = incomingArgs;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

    static class FevalArgs {
        public int nlhs;
        public String func;
        public Object[] args;
    }

    static class FevalResponse {
        public FevalArgs incomingArgs;
        public Object result;
        public String stdout;
        public String stderr;

        public FevalResponse(FevalArgs incomingArgs, Object result, String stdout, String stderr) {
            this.incomingArgs = incomingArgs;
            this.result = result;
            this.stdout = stdout;
            this.stderr = stderr;
        }
    }

}
