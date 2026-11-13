package com.util;

import java.io.Console;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import com.db.DBConnect;

/**
 * Outil de mise à niveau sécurité de la base, à lancer UNE fois (ne fait pas
 * partie de l'application web) :
 *
 * 1. crée la table `admin` si elle n'existe pas ;
 * 2. hache (scrypt) tous les mots de passe encore stockés en clair dans
 *    `user_dtls` et `doctor` (les mots de passe déjà hachés sont ignorés,
 *    l'outil peut donc être relancé sans danger) ;
 * 3. si aucun administrateur n'existe, en crée un. Le mot de passe est demandé
 *    au clavier (sans écho) ; si aucune console n'est disponible, un mot de
 *    passe aléatoire est généré et affiché une seule fois.
 *
 * Usage : java -cp "<classes>;<WEB-INF/lib/*>" com.util.SecuritySetup [email_admin]
 */
public class SecuritySetup {

	public static void main(String[] args) throws Exception {
		String adminEmail = args.length > 0 ? args[0] : "admin@gmail.com";

		Connection conn = DBConnect.getConn();
		if (conn == null) {
			System.err.println("Connexion MySQL impossible : Laragon/MySQL est-il demarre ?");
			System.exit(1);
		}

		Statement st = conn.createStatement();
		st.executeUpdate("create table if not exists admin ("
				+ "id int not null auto_increment primary key, "
				+ "email varchar(225) not null unique, "
				+ "password varchar(225) not null)");
		System.out.println("Table admin : OK");

		System.out.println("Patients haches : " + hashPlaintext(conn, "user_dtls", "user_id"));
		System.out.println("Medecins haches : " + hashPlaintext(conn, "doctor", "id"));

		ResultSet rs = st.executeQuery("select count(*) from admin");
		rs.next();
		if (rs.getInt(1) > 0) {
			System.out.println("Un administrateur existe deja : rien a creer.");
		} else {
			String password = askPassword();
			PreparedStatement ps = conn.prepareStatement("insert into admin(email,password) values(?,?)");
			ps.setString(1, adminEmail);
			ps.setString(2, PasswordUtil.hash(password));
			ps.executeUpdate();
			System.out.println("Administrateur cree : " + adminEmail);
		}
		conn.close();
	}

	private static int hashPlaintext(Connection conn, String table, String idCol) throws Exception {
		int count = 0;
		ResultSet rs = conn.createStatement().executeQuery("select " + idCol + ", password from " + table);
		PreparedStatement upd = conn.prepareStatement("update " + table + " set password=? where " + idCol + "=?");
		while (rs.next()) {
			String stored = rs.getString(2);
			if (stored != null && !PasswordUtil.isHashed(stored)) {
				upd.setString(1, PasswordUtil.hash(stored));
				upd.setInt(2, rs.getInt(1));
				upd.executeUpdate();
				count++;
			}
		}
		return count;
	}

	private static String askPassword() {
		Console console = System.console();
		if (console != null) {
			while (true) {
				char[] p1 = console.readPassword("Mot de passe admin (%d caracteres min.) : ",
						PasswordUtil.MIN_LENGTH);
				char[] p2 = console.readPassword("Confirmation : ");
				String a = new String(p1), b = new String(p2);
				if (!a.equals(b)) {
					System.out.println("Les deux saisies different.");
				} else if (!PasswordUtil.isAcceptable(a)) {
					System.out.println("Mot de passe trop court.");
				} else {
					return a;
				}
			}
		}
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
		SecureRandom random = new SecureRandom();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 16; i++) {
			sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		System.out.println("Pas de console : mot de passe admin genere (notez-le, il ne sera plus affiche) : "
				+ sb);
		return sb.toString();
	}

}
