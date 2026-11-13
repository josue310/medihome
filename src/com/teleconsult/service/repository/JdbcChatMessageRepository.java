package com.teleconsult.service.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.teleconsult.model.ChatMessage;
import com.teleconsult.service.Database;

/** Implémentation JDBC (MySQL) : table consultation_message. */
@ApplicationScoped
public class JdbcChatMessageRepository implements ChatMessageRepository {

	@Inject
	private Database database;

	@Override
	public ChatMessage save(ChatMessage m) {
		String sql = "insert into consultation_message(consultation_id, sender_id, sender_role, content, created_at) "
				+ "values (?,?,?,?,?)";
		Connection c = null;
		try {
			c = database.getConnection();
			PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			ps.setInt(1, m.getConsultationId());
			ps.setString(2, m.getSenderId());
			ps.setString(3, m.getSenderRole().name());
			ps.setString(4, m.getContent());
			ps.setTimestamp(5, new Timestamp(m.getCreatedAt()));
			ps.executeUpdate();
			ResultSet keys = ps.getGeneratedKeys();
			if (keys.next()) {
				m.setId(keys.getLong(1));
			}
			return m;
		} catch (SQLException e) {
			throw new RepositoryException("Chat message insert failed", e);
		} finally {
			Jdbc.close(c);
		}
	}
}
