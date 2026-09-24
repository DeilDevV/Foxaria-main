module.exports = {
  apps: [
    {
      name: "foxaria-proxy",
      cwd: "/opt/foxaria/proxy-bungeecord",
      script: "/opt/foxaria/proxy-bungeecord/start.sh",
      interpreter: "bash",
      autorestart: true,
      watch: false,
      kill_timeout: 30000,
      max_memory_restart: "1500M",
      env: {
        FOXARIA_MODERATION_JDBC_URL: "jdbc:mysql://127.0.0.1:3306/foxaria?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        FOXARIA_DB_HOST: "127.0.0.1",
        FOXARIA_DB_PORT: "3306",
        FOXARIA_DB_NAME: "foxaria",
        FOXARIA_DB_USER: "foxaria",
        FOXARIA_DB_PASSWORD: "Grnkbq2GS7rBj71TWmx"
      }
    },
    {
      name: "foxaria-auth",
      cwd: "/opt/foxaria/auth-server",
      script: "/opt/foxaria/auth-server/start.sh",
      interpreter: "bash",
      autorestart: true,
      watch: false,
      kill_timeout: 30000,
      max_memory_restart: "2500M",
      env: {
        FOXARIA_MODERATION_JDBC_URL: "jdbc:mysql://127.0.0.1:3306/foxaria?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        FOXARIA_DB_HOST: "127.0.0.1",
        FOXARIA_DB_PORT: "3306",
        FOXARIA_DB_NAME: "foxaria",
        FOXARIA_DB_USER: "foxaria",
        FOXARIA_DB_PASSWORD: "Grnkbq2GS7rBj71TWmx"
      }
    },
    {
      name: "foxaria-lobby",
      cwd: "/opt/foxaria/lobby-server",
      script: "/opt/foxaria/lobby-server/start.sh",
      interpreter: "bash",
      autorestart: true,
      watch: false,
      kill_timeout: 30000,
      max_memory_restart: "2500M",
      env: {
        FOXARIA_MODERATION_JDBC_URL: "jdbc:mysql://127.0.0.1:3306/foxaria?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        FOXARIA_DB_HOST: "127.0.0.1",
        FOXARIA_DB_PORT: "3306",
        FOXARIA_DB_NAME: "foxaria",
        FOXARIA_DB_USER: "foxaria",
        FOXARIA_DB_PASSWORD: "Grnkbq2GS7rBj71TWmx"
      }
    },
    {
      name: "foxaria-test",
      cwd: "/opt/foxaria/test-server",
      script: "/opt/foxaria/test-server/start.sh",
      interpreter: "bash",
      autorestart: true,
      watch: false,
      kill_timeout: 30000,
      max_memory_restart: "6G",
      env: {
        FOXARIA_MODERATION_JDBC_URL: "jdbc:mysql://127.0.0.1:3306/foxaria?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
        FOXARIA_DB_HOST: "127.0.0.1",
        FOXARIA_DB_PORT: "3306",
        FOXARIA_DB_NAME: "foxaria",
        FOXARIA_DB_USER: "foxaria",
        FOXARIA_DB_PASSWORD: "Grnkbq2GS7rBj71TWmx"
      }
    },
    {
      name: "foxaria-site-backend",
      cwd: "/opt/foxaria/SITE-FOXARIA/backend",
      script: "server.js",
      interpreter: "node",
      autorestart: true,
      watch: false,
      max_memory_restart: "700M",
      env: {
        NODE_ENV: "production",
        PORT: "3001",
        DB_HOST: "127.0.0.1",
        DB_PORT: "3306",
        DB_NAME: "foxaria",
        DB_USER: "foxaria",
        DB_PASSWORD: "Grnkbq2GS7rBj71TWmx",
        MYSQL_HOST: "127.0.0.1",
        MYSQL_PORT: "3306",
        MYSQL_DATABASE: "foxaria",
        MYSQL_USER: "foxaria",
        MYSQL_PASSWORD: "Grnkbq2GS7rBj71TWmx"
      }
    }
  ]
}
