package com.teleconsult.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.bouncycastle.util.encoders.Hex;

import com.teleconsult.model.Attachment;
import com.teleconsult.model.AuthenticatedParticipant;
import com.teleconsult.model.ChatNotificationEvent;
import com.teleconsult.service.repository.AttachmentRepository;

/**
 * Dépôt sécurisé de documents :
 * - types autorisés : application/pdf, image/jpeg, image/png ; le type déclaré
 *   doit être confirmé par la signature binaire du fichier (« magic bytes »),
 *   ce qui empêche de déposer un exécutable ou du HTML renommé en .pdf ;
 * - taille maximale (10 Mo) contrôlée PENDANT la copie, fichier tronqué supprimé ;
 * - stockage hors de l'application web, sous un nom aléatoire (UUID) : le nom
 *   fourni par l'utilisateur n'est jamais utilisé comme chemin (pas de path traversal) ;
 * - empreinte SHA-256 conservée pour l'intégrité.
 */
@ApplicationScoped
public class AttachmentService {

	private static final Logger LOG = Logger.getLogger(AttachmentService.class.getName());

	private static final byte[] PDF_MAGIC = { '%', 'P', 'D', 'F', '-' };
	private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	private static final byte[] PNG_MAGIC = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };

	@Inject
	private TeleconsultationConfig config;

	@Inject
	private AttachmentRepository repository;

	@Inject
	private Event<ChatNotificationEvent> notifications;

	public Attachment store(AuthenticatedParticipant sender, InputStream content, String declaredMimeType,
			String originalFileName) {
		if (content == null) {
			throw TeleconsultationException.badRequest("FILE_REQUIRED", "A multipart field named 'file' is required");
		}
		String mime = normalizeMime(declaredMimeType);
		byte[] magic = magicFor(mime);
		if (magic == null) {
			throw TeleconsultationException.unsupportedMediaType(
					"Allowed types: application/pdf, image/jpeg, image/png");
		}

		Path dir = new File(config.getUploadDirectory(), String.valueOf(sender.getConsultationId())).toPath();
		String storedName = UUID.randomUUID().toString() + extensionFor(mime);
		Path tmp = null;
		try {
			Files.createDirectories(dir);
			tmp = Files.createTempFile(dir, "upload-", ".part");

			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			long max = config.getMaxUploadBytes();
			long size = copyWithLimit(content, tmp, sha, max);
			if (size == 0) {
				throw TeleconsultationException.badRequest("EMPTY_FILE", "The file is empty");
			}
			if (!startsWith(tmp, magic)) {
				throw TeleconsultationException.unsupportedMediaType(
						"File content does not match the declared type " + mime);
			}

			Path target = dir.resolve(storedName);
			Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
			tmp = null; // déplacé : ne plus le supprimer

			Attachment a = new Attachment();
			a.setConsultationId(sender.getConsultationId());
			a.setSenderId(sender.getUserId());
			a.setSenderRole(sender.getRole());
			a.setOriginalName(sanitizeFileName(originalFileName, extensionFor(mime)));
			a.setStoredName(storedName);
			a.setMimeType(mime);
			a.setSizeBytes(size);
			a.setSha256(Hex.toHexString(sha.digest()));
			a.setCreatedAt(System.currentTimeMillis());
			try {
				repository.save(a);
			} catch (RuntimeException e) {
				Files.deleteIfExists(target); // pas de fichier orphelin sans métadonnées
				throw e;
			}

			Map<String, Object> payload = new LinkedHashMap<String, Object>();
			payload.put("kind", "ATTACHMENT");
			payload.put("attachmentId", a.getId());
			payload.put("senderRole", a.getSenderRole().name());
			payload.put("fileName", a.getOriginalName());
			payload.put("mimeType", a.getMimeType());
			payload.put("sizeBytes", a.getSizeBytes());
			payload.put("createdAt", a.getCreatedAt());
			notifications.fire(new ChatNotificationEvent(sender.getConsultationId(), sender.getUserId(), payload));
			return a;

		} catch (IOException e) {
			throw TeleconsultationException.internal("File storage failed", e);
		} catch (NoSuchAlgorithmException e) {
			throw TeleconsultationException.internal("SHA-256 unavailable", e);
		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (IOException e) {
					LOG.log(Level.WARNING, "Temporary upload not deleted: " + tmp, e);
				}
			}
		}
	}

	/** Copie en comptant les octets ; lève 413 dès que la limite est dépassée. */
	private static long copyWithLimit(InputStream in, Path target, MessageDigest sha, long max) throws IOException {
		long total = 0;
		byte[] buffer = new byte[8192];
		OutputStream out = Files.newOutputStream(target);
		try {
			int n;
			while ((n = in.read(buffer)) != -1) {
				total += n;
				if (total > max) {
					throw TeleconsultationException.payloadTooLarge("File exceeds " + (max / (1024 * 1024)) + " MB");
				}
				sha.update(buffer, 0, n);
				out.write(buffer, 0, n);
			}
		} finally {
			TeleconsultationConfig.closeQuietly(out);
		}
		return total;
	}

	/**
	 * Document téléchargeable d'une consultation, après contrôle d'appartenance.
	 * 404 si le document n'existe pas OU appartient à une autre consultation
	 * (réponse identique : pas d'énumération possible).
	 */
	public Attachment find(AuthenticatedParticipant requester, long attachmentId) {
		Attachment a = repository.findById(requester.getConsultationId(), attachmentId);
		if (a == null || !STORED_NAME.matcher(a.getStoredName()).matches()) {
			throw new TeleconsultationException(404, "ATTACHMENT_NOT_FOUND", "Attachment not found");
		}
		return a;
	}

	/** Fichier physique d'un document (le nom stocké a été validé par find()). */
	public File fileOf(Attachment a) {
		File f = new File(new File(config.getUploadDirectory(), String.valueOf(a.getConsultationId())),
				a.getStoredName());
		if (!f.isFile()) {
			LOG.severe("Fichier manquant pour le document " + a.getId() + " : " + f);
			throw new TeleconsultationException(404, "ATTACHMENT_NOT_FOUND", "Attachment not found");
		}
		return f;
	}

	/** Seuls les noms générés par store() (UUID + extension autorisée) sont acceptés : aucun chemin. */
	private static final java.util.regex.Pattern STORED_NAME = java.util.regex.Pattern
			.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|png|jpg)$");

	private static boolean startsWith(Path file, byte[] magic) throws IOException {
		byte[] head = new byte[magic.length];
		InputStream in = Files.newInputStream(file);
		try {
			int read = 0;
			while (read < head.length) {
				int n = in.read(head, read, head.length - read);
				if (n < 0) {
					return false;
				}
				read += n;
			}
		} finally {
			TeleconsultationConfig.closeQuietly(in);
		}
		return Arrays.equals(head, magic);
	}

	private static String normalizeMime(String mime) {
		if (mime == null) {
			return "";
		}
		int semi = mime.indexOf(';'); // retire d'éventuels paramètres
		return (semi >= 0 ? mime.substring(0, semi) : mime).trim().toLowerCase();
	}

	private static byte[] magicFor(String mime) {
		if ("application/pdf".equals(mime)) {
			return PDF_MAGIC;
		}
		if ("image/jpeg".equals(mime)) {
			return JPEG_MAGIC;
		}
		if ("image/png".equals(mime)) {
			return PNG_MAGIC;
		}
		return null;
	}

	private static String extensionFor(String mime) {
		if ("application/pdf".equals(mime)) {
			return ".pdf";
		}
		return "image/png".equals(mime) ? ".png" : ".jpg";
	}

	/**
	 * Nom d'affichage sûr : sans chemin, caractères limités, 120 caractères max.
	 * (Ce nom n'est jamais utilisé pour accéder au disque.)
	 */
	static String sanitizeFileName(String name, String defaultExtension) {
		String base = name == null ? "" : name;
		base = base.substring(Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\')) + 1);
		base = base.replaceAll("[^\\p{L}\\p{N} ._()-]", "_").trim();
		while (base.startsWith(".")) {
			base = base.substring(1);
		}
		if (base.isEmpty()) {
			base = "document" + defaultExtension;
		}
		return base.length() > 120 ? base.substring(base.length() - 120) : base;
	}
}
