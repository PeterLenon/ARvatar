package com.arvatar.vortex.service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class DatabaseWriter {
    private final DataSource dataSource;
    private final boolean initializeSchema;
    private final AtomicBoolean schemaInitialized = new AtomicBoolean(false);
    private final Object schemaLock = new Object();

    public DatabaseWriter(DataSource dataSource, boolean initializeSchema) {
        this.dataSource = dataSource;
        this.initializeSchema = initializeSchema;
    }

    private void initializeSchemaIfNecessary(Connection connection) throws SQLException {
        if (!initializeSchema || schemaInitialized.get()) {
            return;
        }
        synchronized (schemaLock) {
            if (!initializeSchema || schemaInitialized.get()) {
                return;
            }
            try (Statement stmt = connection.createStatement()) {
                // Enable pgvector
                stmt.execute("CREATE EXTENSION IF NOT EXISTS vector;");

                // person table
                stmt.execute("CREATE TABLE IF NOT EXISTS person (" +
                        "guru_id TEXT PRIMARY KEY," +
                        "display_name TEXT NOT NULL," +
                        "created_at TIMESTAMPTZ DEFAULT NOW()" +
                        ");");

                // video table
                stmt.execute("CREATE TABLE IF NOT EXISTS video (" +
                        "video_id UUID PRIMARY KEY," +
                        "guru_id TEXT NOT NULL REFERENCES person(guru_id)," +
                        "description TEXT," +
                        "storage_uri TEXT," +
                        "created_at TIMESTAMPTZ DEFAULT NOW()" +
                        ");");

                // persona_chunk table
                stmt.execute("CREATE TABLE IF NOT EXISTS persona_chunk (" +
                        "chunk_id UUID PRIMARY KEY," +
                        "guru_id TEXT NOT NULL REFERENCES person(guru_id)," +
                        "video_id  UUID NOT NULL REFERENCES video(video_id)," +
                        "transcript TEXT," +
                        "public_ok BOOLEAN DEFAULT true," +
                        "embedding vector(384)," +
                        "created_at TIMESTAMPTZ DEFAULT NOW()" +
                        ");");

                // indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS persona_chunk_embedding_idx " +
                        "ON persona_chunk USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);");

                stmt.execute("CREATE INDEX IF NOT EXISTS persona_chunk_guru_idx " +
                        "ON persona_chunk(guru_id);");
                schemaInitialized.set(true);
            } catch (SQLException e) {
                schemaInitialized.set(false);
                throw e;
            }
        }
    }

    public void insertPersonIfNotExists(String guruId, String displayName) throws SQLException {
        String sql = "INSERT INTO person (guru_id, display_name) VALUES (?, ?) " +
                "ON CONFLICT (guru_id) DO NOTHING";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            initializeSchemaIfNecessary(connection);
            ps.setString(1, guruId);
            ps.setString(2, displayName);
            ps.executeUpdate();
        }
    }

    public UUID insertVideo(String guruId, String description, String storageUri) throws SQLException {
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO video (video_id, guru_id, description, storage_uri) " +
                "VALUES (?, ?, ?, ?)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            initializeSchemaIfNecessary(connection);
            ps.setObject(1, id);
            ps.setString(2, guruId);
            ps.setString(3, description);
            ps.setString(4, storageUri);
            ps.executeUpdate();
        }
        return id;
    }

    public UUID insertChunk(String guruId, UUID videoId,
                            String transcript,
                            boolean publicOk, float[] embedding) throws SQLException {
        UUID chunkId = UUID.randomUUID();
        String sql = "INSERT INTO persona_chunk " +
                "(chunk_id, guru_id, video_id, transcript, public_ok, embedding) " +
                "VALUES (?, ?, ?, ?, ?, ?::vector)";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            initializeSchemaIfNecessary(connection);
            ps.setObject(1, chunkId);
            ps.setString(2, guruId);
            ps.setObject(3, videoId);
            ps.setString(4, transcript);
            ps.setBoolean(5, publicOk);
            ps.setString(6, toPgVectorLiteral(embedding));
            ps.executeUpdate();
        }
        return chunkId;
    }

    public void updateChunk(UUID chunkId, String newTranscript, boolean publicOk) throws SQLException {
        String sql = "UPDATE persona_chunk " +
                "SET transcript = ?, public_ok = ? " +
                "WHERE chunk_id = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            initializeSchemaIfNecessary(connection);
            ps.setString(1, newTranscript);
            ps.setBoolean(2, publicOk);
            ps.setObject(3, chunkId);
            ps.executeUpdate();
        }
    }

    public void deleteChunk(UUID chunkId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM persona_chunk WHERE chunk_id = ?")) {
            initializeSchemaIfNecessary(connection);
            ps.setObject(1, chunkId);
            ps.executeUpdate();
        }
    }

    private String toPgVectorLiteral(float[] embedding) {
        StringJoiner joiner = new StringJoiner(",");
        for (float f : embedding) {
            joiner.add(Float.toString(f));
        }
        return "[" + joiner.toString() + "]";
    }
}
