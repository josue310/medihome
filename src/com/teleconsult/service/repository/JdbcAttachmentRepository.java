package com.teleconsult.service.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.teleconsult.model.Attachment;
import com.teleconsult.model.Role;
import com.teleconsult.service.Database;

/** Implémentation JDBC (MySQL) : table consultation_attachment. */
@ApplicationScoped
public class JdbcAttachmentRepository implements AttachmentRepository {

	@Inject
	private Database database;

	@Override
	public Attachment save(Attachment a) {
		String sql = "insert into consultation_attachment(consultation_id, sender_id, sender_role, original_name, "
				+ "stored_name, mime_type, size_bytes, sha256, created_at) values (?,?,?,?,?,?,?,?,?)";
		Connection c = null;
		try {
			c = database.getConnection();
			PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			ps.setInt(1, a.getConsultationId());
			ps.setString(2, a.getSenderId());
			ps.setString(3, a.getSenderRole().name());
			ps.setString(4, a.getOriginalName());
			ps.setString(5, a.getStoredName());
			ps.setString(6, a.getMimeType());
			ps.setLong(7, a.getSizeBytes());
			ps.setString(8, a.getSha256());
			ps.setTimestamp(9, new Timestamp(a.getCreatedAt()));
			ps.executeUpdate();
			ResultSet keys = ps.getGeneratedKeys();
			if (keys.next()) {
				a.setId(keys.getLong(1));
			}
			return a;
		} catch (SQLException e) {
			throw new RepositoryException("Attachment insert failed", e);
		} finally {
			Jdbc.close(c);
		}
	}

	@Override
	public Attachment findById(int consultationId, long attachmentId) {
		// Le filtre sur consultation_id empêche d'accéder au document d'une autre consultation (IDOR).
		String sql = "select id, consultation_id, sender_id, sender_role, original_name, stored_name, mime_type, "
				+ "size_bytes, sha256, created_at from consultation_attachment where id=? and consultation_id=?";
		Connection c = null;
		try {
			c = database.getConnection();
			PreparedStatement ps = c.prepareStatement(sql);
			ps.setLong(1, attachmentId);
			ps.setInt(2, consultationId);
			ResultSet rs = ps.executeQuery();
			if (!rs.next()) {
				return null;
			}
			Attachment a = new Attachment();
			a.setId(rs.getLong(1));
			a.setConsultationId(rs.getInt(2));
			a.setSenderId(rs.getString(3));
			a.setSenderRole(Role.valueOf(rs.getString(4)));
			a.setOriginalName(rs.getString(5));
			a.setStoredName(rs.getString(6));
			a.setMimeType(rs.getString(7));
			a.setSizeBytes(rs.getLong(8));
			a.setSha256(rs.getString(9));
			a.setCreatedAt(rs.getTimestamp(10).getTime());
			return a;
		} catch (SQLException e) {
			throw new RepositoryException("Attachment lookup failed", e);
		} finally {
			Jdbc.close(c);
		}
	}
}
