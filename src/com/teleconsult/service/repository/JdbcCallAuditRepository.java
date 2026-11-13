package com.teleconsult.service.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.teleconsult.model.CallAuditRecord;
import com.teleconsult.service.Database;

/** Implémentation JDBC (MySQL) du journal d'audit : table consultation_call_audit. */
@ApplicationScoped
public class JdbcCallAuditRepository implements CallAuditRepository {

	@Inject
	private Database database;

	@Override
	public void recordStart(CallAuditRecord r) {
		// INSERT IGNORE : si la fin a déjà été écrite (course), la ligne existe déjà.
		String sql = "insert ignore into consultation_call_audit"
				+ "(call_id, consultation_id, caller_id, callee_id, started_at) values (?,?,?,?,?)";
		Connection c = null;
		try {
			c = database.getConnection();
			PreparedStatement ps = c.prepareStatement(sql);
			ps.setString(1, r.getCallId());
			ps.setInt(2, r.getConsultationId());
			ps.setString(3, r.getCallerId());
			ps.setString(4, r.getCalleeId());
			ps.setTimestamp(5, new Timestamp(r.getStartedAt()));
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RepositoryException("Audit start failed for call " + r.getCallId(), e);
		} finally {
			Jdbc.close(c);
		}
	}

	@Override
	public void recordEnd(CallAuditRecord r) {
		String update = "update consultation_call_audit set ended_at=?, duration_seconds=?, end_reason=? "
				+ "where call_id=? and ended_at is null";
		String insert = "insert ignore into consultation_call_audit(call_id, consultation_id, caller_id, callee_id, "
				+ "started_at, ended_at, duration_seconds, end_reason) values (?,?,?,?,?,?,?,?)";
		Connection c = null;
		try {
			c = database.getConnection();
			int updated;
			{
				PreparedStatement ps = c.prepareStatement(update);
				ps.setTimestamp(1, new Timestamp(r.getEndedAt()));
				ps.setBigDecimal(2, r.getDurationSeconds());
				ps.setString(3, r.getEndReason().name());
				ps.setString(4, r.getCallId());
				updated = ps.executeUpdate();
			}
			if (updated == 0) {
				// Le début n'est pas (encore) en base : on écrit la ligne complète.
				{
					PreparedStatement ps = c.prepareStatement(insert);
					ps.setString(1, r.getCallId());
					ps.setInt(2, r.getConsultationId());
					ps.setString(3, r.getCallerId());
					ps.setString(4, r.getCalleeId());
					if (r.getStartedAt() != null) {
						ps.setTimestamp(5, new Timestamp(r.getStartedAt()));
					} else {
						ps.setNull(5, Types.TIMESTAMP);
					}
					ps.setTimestamp(6, new Timestamp(r.getEndedAt()));
					ps.setBigDecimal(7, r.getDurationSeconds());
					ps.setString(8, r.getEndReason().name());
					ps.executeUpdate();
				}
			}
		} catch (SQLException e) {
			throw new RepositoryException("Audit end failed for call " + r.getCallId(), e);
		} finally {
			Jdbc.close(c);
		}
	}
}
