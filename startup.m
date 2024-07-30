javaaddpath('build/libs/matlab-http-engine.jar')

s = com.gt.matlab.engine.http.EngineServer();

s.start(8080);

