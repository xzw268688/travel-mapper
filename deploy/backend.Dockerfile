# 放在服务器的 java-app/ 目录，与 app.jar 同级。
# 构建上下文是 java-app/ 目录，服务器上不做 Maven 构建（2GB 机器内存不够）。
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY app.jar app.jar

ENV TZ=Asia/Shanghai

# 1.8 GiB 机器，且已有 nginx + cloudflared 常驻：
# 堆 320m + 元空间 128m，SerialGC 比 G1 更省常驻内存。
# compose 里 mem_limit=640m，给线程栈、直接内存与 CodeCache 留余量。
ENTRYPOINT ["java", \
  "-Xms192m", \
  "-Xmx320m", \
  "-XX:MaxMetaspaceSize=128m", \
  "-XX:+UseSerialGC", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Duser.timezone=Asia/Shanghai", \
  "-Dfile.encoding=UTF-8", \
  "-jar", "/app/app.jar"]