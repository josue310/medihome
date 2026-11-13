package com.util;

/**
 * Échappement HTML des données affichées dans les JSP (protection XSS).
 * Usage dans une JSP : <%=Html.esc(ap.getFullName())%>
 * Les caractères & < > " ' sont remplacés par leurs entités, ce qui empêche
 * une donnée saisie par un utilisateur (nom, maladie, adresse...) d'être
 * interprétée comme du HTML ou du JavaScript, y compris dans un attribut value="...".
 */
public final class Html {

	private Html() {
	}

	public static String esc(Object value) {
		if (value == null) {
			return "";
		}
		String s = value.toString();
		StringBuilder sb = new StringBuilder(s.length() + 16);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '&':
				sb.append("&amp;");
				break;
			case '<':
				sb.append("&lt;");
				break;
			case '>':
				sb.append("&gt;");
				break;
			case '"':
				sb.append("&quot;");
				break;
			case '\'':
				sb.append("&#39;");
				break;
			default:
				sb.append(c);
			}
		}
		return sb.toString();
	}

}
