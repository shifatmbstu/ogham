package fr.sii.ogham.email.sender.impl.sendgrid.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sendgrid.SendGrid.Email;

import fr.sii.ogham.core.exception.mimetype.MimeTypeDetectionException;
import fr.sii.ogham.core.message.content.Content;
import fr.sii.ogham.core.message.content.StringContent;
import fr.sii.ogham.core.mimetype.MimeTypeProvider;
import fr.sii.ogham.email.exception.sendgrid.ContentHandlerException;

/**
 * Content handler that puts plain text or HTML content into email to be sent
 * through SendGrid. MIME type detection is delegated to an instance of
 * {@link MimeTypeProvider}.
 */
public final class StringContentHandler implements SendGridContentHandler {

	private static final Logger LOG = LoggerFactory.getLogger(StringContentHandler.class);

	private final MimeTypeProvider mimeProvider;

	/**
	 * Constructor.
	 * 
	 * @param mimeProvider
	 *            an object in charge of determining the MIME type of the
	 *            messages to send
	 */
	public StringContentHandler(final MimeTypeProvider mimeProvider) {
		if (mimeProvider == null) {
			throw new IllegalArgumentException("[mimeProvider] cannot be null");
		}

		this.mimeProvider = mimeProvider;
	}

	/**
	 * Reads the content and adds it into the email. This method is expected to
	 * update the content of the {@code email} parameter.
	 * 
	 * While the method signature accepts any {@link Content} instance as
	 * parameter, the method will fail if anything other than a
	 * {@link StringContent} is provided.
	 * 
	 * @param email
	 *            the email to put the content in
	 * @param content
	 *            the unprocessed content
	 * @throws ContentHandlerException
	 *             the handler is unable to add the content to the email
	 * @throws IllegalArgumentException
	 *             the content provided is not of the right type
	 */
	@Override
	public void setContent(final Email email, final Content content) throws ContentHandlerException {
		if (email == null) {
			throw new IllegalArgumentException("[email] cannot be null");
		}
		if (content == null) {
			throw new IllegalArgumentException("[content] cannot be null");
		}

		if (content instanceof StringContent) {
			final String contentStr = ((StringContent) content).getContent();

			try {
				final String mime = mimeProvider.detect(contentStr).toString();
				LOG.debug("Email content {} has detected type {}", content, mime);
				setMimeContent(email, contentStr, mime);
			} catch (MimeTypeDetectionException e) {
				throw new ContentHandlerException("Unable to set the email content", e);
			}
		} else {
			throw new IllegalArgumentException("This instance can only work with StringContent instances, but was passed " + content.getClass().getSimpleName());
		}

	}

	private void setMimeContent(final Email email, final String contentStr, final String mime) throws ContentHandlerException {
		if ("text/plain".equals(mime)) {
			email.setText(contentStr);
		} else if ("text/html".equals(mime)) {
			email.setHtml(contentStr);
		} else {
			throw new ContentHandlerException("MIME type " + mime + " is not supported");
		}
	}

}
