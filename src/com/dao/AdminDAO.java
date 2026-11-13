package com.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.util.PasswordUtil;

/**
 * Comptes administrateur stockés en base (table `admin`), avec mot de passe
 * haché scrypt. Remplace les identifiants codés en dur (admin@gmail.com/admin).
 */
public class AdminDAO {

	private Connection conn;

	public AdminDAO(Connection conn) {
		this.conn = conn;
	}

	public boolean login(String email, String password) {
		try {
			PreparedStatement ps = conn.prepareStatement("select password from admin where email=?");
			ps.setString(1, email);
			ResultSet rs = ps.executeQuery();
			if (rs.next()) {
				return PasswordUtil.verify(password, rs.getString(1));
			}
			PasswordUtil.verifyDummy(password); // même durée que pour un compte existant
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

}
