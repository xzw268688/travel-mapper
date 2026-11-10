package com.travelmapper.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.time.Duration;

/**
 * Supports two modes:
 *  - containers (default): Testcontainers-managed MySQL/Redis/object storage
 *  - external: services already running, selected with -Dtest.containers.enabled=false
 *
 * The external mode exists because our local Docker 29 installation exposes a named
 * pipe that the bundled Testcontainers Docker client cannot negotiate.
 */
public final class TestInfrastructure {

    public static final boolean CONTAINERS_ENABLED = !"false".equalsIgnoreCase(
            System.getProperty("test.containers.enabled", "true"));

    public static final boolean OBJECT_STORAGE_AVAILABLE;
    public static final boolean PRIVATE_BUCKET_ENFORCED;

    private static final String MYSQL_URL;
    private static final String MYSQL_USERNAME;
    private static final String MYSQL_PASSWORD;
    private static final String MYSQL_ADMIN_URL;
    private static final String MYSQL_ADMIN_USERNAME;
    private static final String MYSQL_ADMIN_PASSWORD;
    private static final String REDIS_HOST;
    private static final int REDIS_PORT;
    private static final String OBJECT_STORAGE_ENDPOINT;
    private static final String OBJECT_STORAGE_ACCESS_KEY;
    private static final String OBJECT_STORAGE_SECRET_KEY;

    // Keep container references alive for the JVM lifetime.
    private static MySQLContainer<?> mysqlContainer;
    private static GenericContainer<?> redisContainer;
    private static GenericContainer<?> objectStorageContainer;

    static {
        String mysqlUrl;
        String mysqlUsername;
        String mysqlPassword;
        String mysqlAdminUrl;
        String mysqlAdminUsername;
        String mysqlAdminPassword;
        String redisHost;
        int redisPort;
        String objectStorageEndpoint;
        String objectStorageAccessKey;
        String objectStorageSecretKey;
        boolean objectStorageAvailable;
        boolean privateBucketEnforced;

        if (CONTAINERS_ENABLED) {
            String dbTimeZone = System.getProperty("test.mysql.time-zone", "+08:00");
            String connectionTimeZone = "+00:00".equals(dbTimeZone) ? "UTC" : "Asia/Shanghai";
            mysqlContainer = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("travelmapper")
                    .withUsername("travelmapper")
                    .withPassword("travelmapper")
                    .withEnv("MYSQL_ROOT_PASSWORD", "root")
                    .withEnv("TZ", "UTC".equals(connectionTimeZone) ? "UTC" : "Asia/Shanghai")
                    .withCommand("--default-time-zone=" + dbTimeZone)
                    .withUrlParam("connectionTimeZone", connectionTimeZone)
                    .withUrlParam("forceConnectionTimeZoneToSession", "true")
                    .withStartupTimeout(Duration.ofMinutes(3));
            redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379)
                    .withStartupTimeout(Duration.ofMinutes(2));
            mysqlContainer.start();
            redisContainer.start();

            mysqlUrl = mysqlContainer.getJdbcUrl();
            mysqlUsername = mysqlContainer.getUsername();
            mysqlPassword = mysqlContainer.getPassword();
            mysqlAdminUrl = mysqlContainer.getJdbcUrl();
            mysqlAdminUsername = "root";
            mysqlAdminPassword = "root";
            redisHost = redisContainer.getHost();
            redisPort = redisContainer.getMappedPort(6379);

            String kind = System.getProperty("test.object-storage.kind", "s3mock");
            int containerPort = "minio".equals(kind) ? 9000 : 9090;
            objectStorageAccessKey = "minio".equals(kind) ? "minioadmin" : "test";
            objectStorageSecretKey = objectStorageAccessKey;
            privateBucketEnforced = "minio".equals(kind);
            objectStorageEndpoint = startObjectStorage(kind, containerPort);
            objectStorageAvailable = objectStorageEndpoint != null;
            if (objectStorageAvailable) {
                createBucket(objectStorageEndpoint, objectStorageAccessKey,
                        objectStorageSecretKey, "travelmapper");
                createBucket(objectStorageEndpoint, objectStorageAccessKey,
                        objectStorageSecretKey, "travelmapper2");
            }
        } else {
            mysqlUrl = System.getProperty("test.mysql.url",
                    "jdbc:mysql://127.0.0.1:13306/travelmapper"
                            + "?useUnicode=true&characterEncoding=utf8"
                            + "&connectionTimeZone=Asia/Shanghai&forceConnectionTimeZoneToSession=true"
                            + "&useSSL=false&allowPublicKeyRetrieval=true");
            mysqlUsername = System.getProperty("test.mysql.username", "travelmapper");
            mysqlPassword = System.getProperty("test.mysql.password", "travelmapper");
            mysqlAdminUrl = System.getProperty("test.mysql.admin-url", mysqlUrl);
            mysqlAdminUsername = System.getProperty("test.mysql.admin-username", "root");
            mysqlAdminPassword = System.getProperty("test.mysql.admin-password", "root");
            redisHost = System.getProperty("test.redis.host", "127.0.0.1");
            redisPort = Integer.parseInt(System.getProperty("test.redis.port", "16379"));
            objectStorageEndpoint = System.getProperty("test.object-storage.endpoint",
                    "http://127.0.0.1:19090");
            objectStorageAccessKey = System.getProperty("test.object-storage.access-key", "test");
            objectStorageSecretKey = System.getProperty("test.object-storage.secret-key", "test");
            privateBucketEnforced = Boolean.parseBoolean(
                    System.getProperty("test.object-storage.private-enforced", "false"));
            boolean enabled = !"false".equalsIgnoreCase(
                    System.getProperty("test.object-storage.enabled", "true"));
            objectStorageAvailable = enabled && createBucket(objectStorageEndpoint,
                    objectStorageAccessKey, objectStorageSecretKey, "travelmapper")
                    && createBucket(objectStorageEndpoint, objectStorageAccessKey,
                    objectStorageSecretKey, "travelmapper2");
            if (!objectStorageAvailable) {
                objectStorageEndpoint = null;
            }
        }

        MYSQL_URL = mysqlUrl;
        MYSQL_USERNAME = mysqlUsername;
        MYSQL_PASSWORD = mysqlPassword;
        MYSQL_ADMIN_URL = mysqlAdminUrl;
        MYSQL_ADMIN_USERNAME = mysqlAdminUsername;
        MYSQL_ADMIN_PASSWORD = mysqlAdminPassword;
        REDIS_HOST = redisHost;
        REDIS_PORT = redisPort;
        OBJECT_STORAGE_ENDPOINT = objectStorageEndpoint;
        OBJECT_STORAGE_ACCESS_KEY = objectStorageAccessKey;
        OBJECT_STORAGE_SECRET_KEY = objectStorageSecretKey;
        OBJECT_STORAGE_AVAILABLE = objectStorageAvailable;
        PRIVATE_BUCKET_ENFORCED = privateBucketEnforced;
    }

    private TestInfrastructure() {
    }

    public static String mysqlJdbcUrl() {
        return MYSQL_URL;
    }

    public static String mysqlUsername() {
        return MYSQL_USERNAME;
    }

    public static String mysqlPassword() {
        return MYSQL_PASSWORD;
    }

    public static String mysqlAdminJdbcUrl() {
        return MYSQL_ADMIN_URL;
    }

    public static String mysqlAdminUsername() {
        return MYSQL_ADMIN_USERNAME;
    }

    public static String mysqlAdminPassword() {
        return MYSQL_ADMIN_PASSWORD;
    }

    public static String redisHost() {
        return REDIS_HOST;
    }

    public static int redisPort() {
        return REDIS_PORT;
    }

    public static String objectStorageEndpoint() {
        return OBJECT_STORAGE_ENDPOINT;
    }

    public static String objectStorageAccessKey() {
        return OBJECT_STORAGE_ACCESS_KEY;
    }

    public static String objectStorageSecretKey() {
        return OBJECT_STORAGE_SECRET_KEY;
    }

    private static String startObjectStorage(String kind, int port) {
        try {
            if ("minio".equals(kind)) {
                objectStorageContainer = new GenericContainer<>(DockerImageName.parse(
                        System.getProperty("test.object-storage.image", "minio/minio:latest")))
                        .withExposedPorts(9000)
                        .withEnv("MINIO_ROOT_USER", "minioadmin")
                        .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
                        .withCommand("server /data")
                        .waitingFor(Wait.forHttp("/minio/health/live")
                                .forPort(9000)
                                .withStartupTimeout(Duration.ofSeconds(90)));
            } else {
                objectStorageContainer = new GenericContainer<>(DockerImageName.parse(
                        System.getProperty("test.object-storage.image", "adobe/s3mock:latest")))
                        .withExposedPorts(9090)
                        .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS",
                                "travelmapper,travelmapper2")
                        .waitingFor(Wait.forListeningPort()
                                .withStartupTimeout(Duration.ofSeconds(90)));
            }
            objectStorageContainer.start();
            return "http://" + objectStorageContainer.getHost() + ":"
                    + objectStorageContainer.getMappedPort(port);
        } catch (Throwable ex) {
            System.err.println("Object storage test container unavailable: " + ex.getMessage());
            return null;
        }
    }

    private static boolean createBucket(String endpoint, String accessKey, String secretKey, String bucket) {
        if (endpoint == null) {
            return false;
        }
        try (S3Client client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("us-east-1"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .forcePathStyle(true)
                .build()) {
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            return true;
        } catch (S3Exception ex) {
            return ex.statusCode() == 409;
        } catch (Exception ex) {
            System.err.println("Object storage bucket setup failed: " + ex.getMessage());
            return false;
        }
    }
}
