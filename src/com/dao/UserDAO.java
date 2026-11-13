package com.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.entity.User;
import com.util.PasswordUtil;

public class UserDAO {

	private Connection conn;

	public UserDAO(Connection conn) {
		super();
		this.conn = conn;
	}

	/** Enregistre un patient ; le mot de passe est haché (scrypt) avant stockage. */
	public boolean register(User u) {
		boolean f = false;

		try {
			String sql = "insert into user_dtls(full_name,email,password) values(?,?,?) ";

			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, u.getFullName());
			ps.setString(2, u.getEmail());
			ps.setString(3, PasswordUtil.hash(u.getPassword()));

			int i = ps.executeUpdate();

			if (i == 1) {
				f = true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return f;
	}

	/**
	 * Recherche par email puis vérifie le hachage en Java (le mot de passe
	 * n'apparaît plus jamais dans une requête SQL).
	 */
	public User login(String em, String psw) {
		User u = null;

		try {
			String sql = "select * from user_dtls where email=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, em);

			ResultSet rs = ps.executeQuery();

			// Un même email peut exister plusieurs fois dans les données existantes :
			// on teste chaque compte (le premier dont le mot de passe correspond l'emporte).
			boolean found = false;
			while (u == null && rs.next()) {
				found = true;
				if (PasswordUtil.verify(psw, rs.getString(4))) {
					u = new User();
					u.setId(rs.getInt(1));
					u.setFullName(rs.getString(2));
					u.setEmail(rs.getString(3));
					// le hachage n'est pas conservé dans l'objet mis en session
				}
			}
			if (!found) {
				PasswordUtil.verifyDummy(psw); // même durée que pour un compte existant
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return u;
	}

	public boolean checkOldPassword(int userid, String oldPassword) {
		boolean f = false;

		try {
			String sql = "select password from user_dtls where user_id=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setInt(1, userid);

			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				f = PasswordUtil.verify(oldPassword, rs.getString(1));
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return f;
	}

	public boolean changePassword(int userid, String newPassword) {
		boolean f = false;

		try {
			String sql = "update user_dtls set password=? where user_id=?";
			PreparedStatement ps = conn.prepareStatement(sql);
			ps.setString(1, PasswordUtil.hash(newPassword));
			ps.setInt(2, userid);

			int i = ps.executeUpdate();
			if (i == 1) {
				f = true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return f;
	}

}
