package fr.sii.ogham.sms.builder;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.sii.ogham.core.builder.Builder;
import fr.sii.ogham.core.builder.ContentTranslatorBuilder;
import fr.sii.ogham.core.builder.MessageFillerBuilder;
import fr.sii.ogham.core.builder.MessagingSenderBuilder;
import fr.sii.ogham.core.builder.TemplateBuilder;
import fr.sii.ogham.core.condition.AndCondition;
import fr.sii.ogham.core.condition.Condition;
import fr.sii.ogham.core.condition.RequiredClassCondition;
import fr.sii.ogham.core.condition.RequiredPropertyCondition;
import fr.sii.ogham.core.exception.builder.BuildException;
import fr.sii.ogham.core.filler.MessageFiller;
import fr.sii.ogham.core.message.Message;
import fr.sii.ogham.core.sender.ConditionalSender;
import fr.sii.ogham.core.sender.ContentTranslatorSender;
import fr.sii.ogham.core.sender.FillerSender;
import fr.sii.ogham.core.sender.MessageSender;
import fr.sii.ogham.core.sender.MultiImplementationSender;
import fr.sii.ogham.core.translator.content.ContentTranslator;
import fr.sii.ogham.core.util.BuilderUtils;
import fr.sii.ogham.sms.SmsConstants;
import fr.sii.ogham.sms.message.addressing.translator.PhoneNumberTranslator;
import fr.sii.ogham.sms.sender.SmsSender;
import fr.sii.ogham.sms.sender.impl.CloudhopperSMPPSender;
import fr.sii.ogham.sms.sender.impl.OvhSmsSender;
import fr.sii.ogham.sms.sender.impl.PhoneNumberTranslatorSender;
import fr.sii.ogham.template.TemplateConstants;

/**
 * <p>
 * Specialized builder for SMS sender.
 * </p>
 * There exists several implementations to send an SMS:
 * <ul>
 * <li>Using <a
 * href="https://github.com/twitter/cloudhopper-smpp">cloudhopper</a> (SMPP
 * library)</li>
 * <li>Using <a href="http://jsmpp.org/">jsmpp</a> (SMPP library)</li>
 * <li>Using any other library</li>
 * <li>Using HTTP request to drive <a href="http://guides.ovh.com/Http2Sms">OVH
 * API</a></li>
 * <li>Using REST, HTTP or SOAP requests to drive <a
 * href="http://www.smsglobal.com/">smsglobal APIs</a></li>
 * <li>Using any other web service</li>
 * <li>...</li>
 * </ul>
 * <p>
 * This builder provides a {@link MultiImplementationSender}. The aim of the
 * {@link MultiImplementationSender} is to choose the best implementation for
 * sending the SMS according to the runtime environment (detection of libraries
 * in the classpath, availability of a particular property, ...).
 * </p>
 * <p>
 * This builder lets you the possibility to register any new implementation. It
 * allows you to enable or not templating support and automatic filling of
 * message values (like sender address for example).
 * </p>
 * 
 * @author Aurélien Baudet
 * @see SmsSender
 * @see OvhSmsSender
 * @see CloudhopperSMPPSender
 * @see TemplateBuilder
 * @see ContentTranslatorBuilder
 * @see MessageFillerBuilder
 */
public class SmsBuilder implements MessagingSenderBuilder<ConditionalSender> {
	private static final Logger LOG = LoggerFactory.getLogger(SmsBuilder.class);

	/**
	 * The sender instance constructed by this builder
	 */
	private ConditionalSender sender;

	/**
	 * The specialized {@link MultiImplementationSender}. It is useful to
	 * register new implementations
	 */
	private SmsSender smsSender;

	/**
	 * The builder for message filler used to add values to the message
	 */
	private MessageFillerBuilder messageFillerBuilder;

	/**
	 * Map of possible implementations with associated conditions
	 */
	private final Map<Condition<Message>, Builder<? extends MessageSender>> implementations;

	/**
	 * The builder for the translator that will update the content of the
	 * message
	 */
	private ContentTranslatorBuilder contentTranslatorBuilder;

	/**
	 * Builder for phone number transformations for receiver
	 */
	private PhoneNumberTranslatorBuilder recipientNumberTranslatorBuilder;

	/**
	 * Builder for phone number transformations for sender
	 */
	private PhoneNumberTranslatorBuilder senderNumberTranslatorBuilder;

	/**
	 * Own property key for template resolution prefix
	 */
	private String templatePrefixKey;

	/**
	 * Own property key for template resolution prefix
	 */
	private String templateSuffixKey;

	public SmsBuilder() {
		super();
		smsSender = new SmsSender();
		sender = smsSender;
		implementations = new HashMap<>();
	}

	@Override
	public ConditionalSender build() throws BuildException {
		for (Entry<Condition<Message>, Builder<? extends MessageSender>> impl : implementations.entrySet()) {
			MessageSender s = impl.getValue().build();
			LOG.debug("Implementation {} registered", s);
			smsSender.addImplementation(impl.getKey(), s);
		}
		if (contentTranslatorBuilder != null) {
			if (templatePrefixKey != null) {
				LOG.debug("Use custom property key {} for prefix template resolution", templatePrefixKey);
				getTemplateBuilder().setPrefixKey(templatePrefixKey);
			}
			if (templateSuffixKey != null) {
				LOG.debug("Use custom property key {} for suffix template resolution", templateSuffixKey);
				getTemplateBuilder().setSuffixKey(templateSuffixKey);
			}
			sender = new ContentTranslatorSender(contentTranslatorBuilder.build(), sender);
		}
		if (senderNumberTranslatorBuilder == null) {
			LOG.debug("Using default phone number translation for sender phone number");
			senderNumberTranslatorBuilder = new DefaultPhoneNumberTranslatorBuilder();
		}
		if (recipientNumberTranslatorBuilder == null) {
			LOG.debug("Using default phone number translation for recipient phone number");
			recipientNumberTranslatorBuilder = new DefaultPhoneNumberTranslatorBuilder();
		}
		sender = new PhoneNumberTranslatorSender(senderNumberTranslatorBuilder.build(), recipientNumberTranslatorBuilder.build(), sender);
		if (messageFillerBuilder != null) {
			MessageFiller messageFiller = messageFillerBuilder.build();
			LOG.debug("Automatic filling of message enabled {}", messageFiller);
			sender = new FillerSender(messageFiller, sender);
		}
		return sender;
	}

	/**
	 * Tells the builder to use all default behaviors and values:
	 * <ul>
	 * <li>Registers OVH HTTP API implementation</li>
	 * <li>Enables automatic filling of message based on configuration
	 * properties</li>
	 * <li>Enables templating support</li>
	 * </ul>
	 * <p>
	 * Configuration values come from system properties.
	 * </p>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder useDefaults() {
		return useDefaults(BuilderUtils.getDefaultProperties());
	}

	/**
	 * Tells the builder to use all default behaviors and values:
	 * <ul>
	 * <li>Registers OVH HTTP API implementation</li>
	 * <li>Enables automatic filling of message based on configuration
	 * properties</li>
	 * <li>Enables templating support</li>
	 * </ul>
	 * <p>
	 * Configuration values come from provided properties.
	 * </p>
	 * 
	 * @param properties
	 *            the properties to use instead of default ones
	 * @return this instance for fluent use
	 */
	public SmsBuilder useDefaults(Properties properties) {
		registerDefaultImplementations(properties);
		withPhoneNumberTranslation();
		withAutoFilling(properties);
		withTemplate();
		return this;
	}

	/**
	 * Register a new implementation for sending SMS. The implementation is
	 * associated to a condition. If the condition evaluation returns true at
	 * runtime then it means that the implementation can be used. If several
	 * implementations are available, only the first implementation is really
	 * invoked.
	 * 
	 * @param condition
	 *            the condition that indicates at runtime if the implementation
	 *            can be used or not
	 * @param implementation
	 *            the implementation to register
	 * @return this instance for fluent use
	 */
	public SmsBuilder registerImplementation(Condition<Message> condition, MessageSender implementation) {
		smsSender.addImplementation(condition, implementation);
		return this;
	}

	/**
	 * Register a new implementation for sending SMS. The implementation is
	 * associated to a condition. If the condition evaluation returns true at
	 * runtime then it means that the implementation can be used. If several
	 * implementations are available, only the first implementation is really
	 * invoked.
	 * 
	 * @param condition
	 *            the condition that indicates at runtime if the implementation
	 *            can be used or not
	 * @param builder
	 *            the builder for the implementation to register
	 * @return this instance for fluent use
	 */
	public SmsBuilder registerImplementation(Condition<Message> condition, Builder<? extends MessageSender> builder) {
		implementations.put(condition, builder);
		return this;
	}

	/**
	 * Register all default implementations:
	 * <ul>
	 * <li>OVH HTTP API implementation</li>
	 * </ul>
	 * <p>
	 * Configuration values come from system properties.
	 * </p>
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder registerDefaultImplementations() {
		return registerDefaultImplementations(System.getProperties());
	}

	/**
	 * Register all default implementations:
	 * <ul>
	 * <li>OVH HTTP API implementation</li>
	 * <li>smsgloabl REST API implementation</li>
	 * <li>Cloudhopper SMPP implementation</li>
	 * </ul>
	 * <p>
	 * Configuration values come from provided properties.
	 * </p>
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @param properties
	 *            the properties to use
	 * @return this instance for fluent use
	 */
	public SmsBuilder registerDefaultImplementations(Properties properties) {
		withOvhHttpApi(properties);
		withSmsglobalRestApi(properties);
		withCloudhopper(properties);
		return this;
	}

	/**
	 * Enable smsglobal REST API implementation. This implementation is used
	 * only if the associated condition indicates that smsglobal REST API can be
	 * used. The condition checks if:
	 * <ul>
	 * <li>The property <code>ogham.sms.smsglobal.api.key</code> is set</li>
	 * </ul>
	 * 
	 * @param properties
	 *            the properties to use for checking if property exists
	 * @return this builder instance for fluent use
	 */
	public SmsBuilder withSmsglobalRestApi(Properties properties) {
		// Use smsglobal REST API only if
		// SmsConstants.SMSGLOBAL_REST_API_KEY_PROPERTY is set
//		registerImplementation(new RequiredPropertyCondition<Message>(SmsConstants.SmsGlobal.SMSGLOBAL_REST_API_KEY_PROPERTY, properties), new SmsglobalRestSender());
		return this;
	}

	/**
	 * Enable OVH HTTP API implementation. This implementation is used only if
	 * the associated condition indicates that OVH HTTP API can be used. The
	 * condition checks if:
	 * <ul>
	 * <li>The property <code>ogham.sms.ovh.account</code> is set</li>
	 * <li>The property <code>ogham.sms.ovh.login</code> is set</li>
	 * <li>The property <code>ogham.sms.ovh.password</code> is set</li>
	 * </ul>
	 * 
	 * @param properties
	 *            the properties to use for checking if property exists
	 * @return this builder instance for fluent use
	 */
	public SmsBuilder withOvhHttpApi(Properties properties) {
		try {
			// Use OVH implementation only if SmsConstants.ACCOUNT_PROPERTY is
			// set
			// @formatter:off
			registerImplementation(new AndCondition<>(
										new RequiredPropertyCondition<Message>(SmsConstants.OvhConstants.ACCOUNT_PROPERTY, properties),
										new RequiredPropertyCondition<Message>(SmsConstants.OvhConstants.LOGIN_PROPERTY, properties),
										new RequiredPropertyCondition<Message>(SmsConstants.OvhConstants.PASSWORD_PROPERTY, properties)),
					new OvhSmsBuilder().useDefaults(properties));
			// @formatter:on
		} catch (Exception e) {
			LOG.debug("Can't register OVH implementation", e);
		}
		return this;
	}

	/**
	 * Enable Cloudhoppder SMPP implementation. This implementation is used only
	 * if the associated condition indicates that Cloudhopper SMPP can be used.
	 * The condition checks if:
	 * <ul>
	 * <li>The property <code>ogham.sms.smpp.host</code> is set</li>
	 * <li>The property <code>ogham.sms.smpp.port</code> is set</li>
	 * <li>The class <code>com.cloudhopper.smpp.SmppClient</code> is available
	 * in the classpath</li>
	 * </ul>
	 * The registration can silently fail if the ch-smpp jar is not in the
	 * classpath. In this case, the Cloudhopper implementation is not registered
	 * at all.
	 * 
	 * @param properties
	 *            the properties to use for checking if property exists
	 * @return this builder instance for fluent use
	 */
	public SmsBuilder withCloudhopper(Properties properties) {
		try {
			// Use Cloudhopper SMPP implementation only if SmppClient class is
			// in the classpath and the SmppConstants.SMPP_HOST_PROPERTY
			// property is set
			// @formatter:off
			registerImplementation(new AndCondition<>(
										new RequiredPropertyCondition<Message>(SmsConstants.SmppConstants.HOST_PROPERTY, properties),
										new RequiredPropertyCondition<Message>(SmsConstants.SmppConstants.PORT_PROPERTY, properties),
										new RequiredClassCondition<Message>("com.cloudhopper.smpp.SmppClient")),
					new CloudhopperSMPPBuilder().useDefaults(properties));
			// @formatter:on
		} catch (Exception e) {
			LOG.debug("Can't register Cloudhopper implementation", e);
		}
		return this;
	}

	/**
	 * Enables automatic filling of SMS with values that come from multiple
	 * sources. It let you use your own builder instead of using default
	 * behaviors.
	 * 
	 * @param builder
	 *            the builder for constructing the message filler
	 * @return this instance for fluent use
	 */
	public SmsBuilder withAutoFilling(MessageFillerBuilder builder) {
		messageFillerBuilder = builder;
		return this;
	}

	/**
	 * Enables filling of SMS with values that comes from provided configuration
	 * properties.
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @param props
	 *            the properties that contains the values to set on the SMS
	 * @param baseKeys
	 *            the prefix(es) for the keys used for filling the message
	 * @return this instance for fluent use
	 */
	public SmsBuilder withAutoFilling(Properties props, String... baseKeys) {
		withAutoFilling(new MessageFillerBuilder().useDefaults(props, baseKeys));
		return this;
	}

	/**
	 * Enables filling of SMS with values that comes from provided configuration
	 * properties. It uses the default prefix for the keys ("sms" and
	 * "ogham.sms").
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @param props
	 *            the properties that contains the values to set on the SMS
	 * @return this instance for fluent use
	 */
	public SmsBuilder withAutoFilling(Properties props) {
		return withAutoFilling(props, SmsConstants.FILL_PREFIXES);
	}

	/**
	 * Enables filling of SMS with values that comes from system configuration
	 * properties. It uses the default prefixes for the keys ("sms" and
	 * "ogham.sms").
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder withAutoFilling() {
		withAutoFilling(BuilderUtils.getDefaultProperties());
		return this;
	}

	/**
	 * Enables templating support using all default behaviors and values. See
	 * {@link ContentTranslatorBuilder#useDefaults()} for more information.
	 * 
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder withTemplate() {
		return withTemplate(new ContentTranslatorBuilder().useDefaults());
	}

	/**
	 * Enables templating support using the provided
	 * {@link ContentTranslatorBuilder}. It decorates the SMS sender with a
	 * {@link ContentTranslatorSender}.
	 * 
	 * @param builder
	 *            the builder to use to build the {@link ContentTranslator}
	 *            instead of using the default one
	 * @return this instance for fluent use
	 */
	public SmsBuilder withTemplate(ContentTranslatorBuilder builder) {
		contentTranslatorBuilder = builder;
		return this;
	}

	/**
	 * <p>
	 * Calling this method will enable different location for SMS templates from
	 * default one. The location will be specified by different property keys
	 * for prefix and suffix.
	 * </p>
	 * 
	 * By default default properties are:
	 * <ul>
	 * <li>ogham.template.prefix (see {@link TemplateConstants#PREFIX_PROPERTY})
	 * </li>
	 * <li>ogham.template.suffix (see {@link TemplateConstants#SUFFIX_PROPERTY}</li>
	 * </ul>
	 * 
	 * Calling this method will change the property keys to:
	 * <ul>
	 * <li>ogham.sms.template.prefix (see
	 * {@link fr.sii.ogham.sms.SmsConstants.TemplateConstants#PREFIX_PROPERTY}</li>
	 * <li>ogham.sms.template.suffix (see
	 * {@link fr.sii.ogham.sms.SmsConstants.TemplateConstants#SUFFIX_PROPERTY}</li>
	 * </ul>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder enableSmsTemplateKeys() {
		setTemplatePrefixKey(SmsConstants.TemplateConstants.PREFIX_PROPERTY);
		setTemplateSuffixKey(SmsConstants.TemplateConstants.SUFFIX_PROPERTY);
		return this;
	}

	/**
	 * <p>
	 * Calling this method will enable different location for SMS templates from
	 * default one. The location will be specified by a different property key
	 * for prefix.
	 * </p>
	 * 
	 * <p>
	 * By default default property key is ogham.template.prefix (see
	 * {@link TemplateConstants#PREFIX_PROPERTY})
	 * </p>
	 * 
	 * <p>
	 * Calling this method will change the property key to the provided key.
	 * </p>
	 * 
	 * @param prefixKey
	 *            the new key for the SMS template prefix
	 * @return this instance for fluent use
	 */
	public SmsBuilder setTemplatePrefixKey(String prefixKey) {
		this.templatePrefixKey = prefixKey;
		return this;
	}

	/**
	 * <p>
	 * Calling this method will enable different location for SMS templates from
	 * default one. The location will be specified by a different property key
	 * for suffix.
	 * </p>
	 * 
	 * <p>
	 * By default default property key is ogham.template.prefix (see
	 * {@link TemplateConstants#SUFFIX_PROPERTY})
	 * </p>
	 * 
	 * <p>
	 * Calling this method will change the property key to the provided key.
	 * </p>
	 * 
	 * @param suffixKey
	 *            the new key for the SMS template suffix
	 * @return this instance for fluent use
	 */
	public SmsBuilder setTemplateSuffixKey(String suffixKey) {
		this.templateSuffixKey = suffixKey;
		return this;
	}

	/**
	 * Enables Addressing strategy using all default behaviors and values. See
	 * {@link SenderPhoneNumberTranslatorBuilder} and
	 * {@link RecipientPhoneNumberTranslatorBuilder}.
	 * 
	 * <p>
	 * Automatically called by {@link #useDefaults()} and
	 * {@link #useDefaults(Properties)}
	 * </p>
	 * 
	 * @return this instance for fluent use
	 */
	public SmsBuilder withPhoneNumberTranslation() {
		return withPhoneNumberTranslation(new SenderPhoneNumberTranslatorBuilder().useDefaults(), new RecipientPhoneNumberTranslatorBuilder().useDefaults());
	}

	/**
	 * Enables Addressing strategy using the provided
	 * {@link SenderPhoneNumberTranslatorBuilder} and
	 * {@link RecipientPhoneNumberTranslatorBuilder}. It decorates the SMS
	 * sender with a {@link PhoneNumberTranslatorSender}
	 * 
	 * @param senderBuilder
	 *            the builder to use to build the {@link PhoneNumberTranslator}
	 *            for sender instead of using the default one
	 * @param recipientBuilder
	 *            the builder to use to build the {@link PhoneNumberTranslator}
	 *            for sender instead of using the default one
	 * @return this instance for fluent use
	 */
	public SmsBuilder withPhoneNumberTranslation(PhoneNumberTranslatorBuilder senderBuilder, PhoneNumberTranslatorBuilder recipientBuilder) {
		withSenderPhoneNumberTranslation(senderBuilder);
		withReceiverPhoneNumberTranslation(recipientBuilder);
		return this;
	}

	/**
	 * Enables Addressing strategy using the provided
	 * {@link RecipientPhoneNumberTranslatorBuilder}. It decorates the SMS
	 * sender with a {@link PhoneNumberTranslatorSender}
	 * 
	 * @param builder
	 *            the builder to use to build the {@link PhoneNumberTranslator}
	 *            instead of using the default one
	 * @return this instance for fluent use
	 */
	public SmsBuilder withReceiverPhoneNumberTranslation(PhoneNumberTranslatorBuilder builder) {
		recipientNumberTranslatorBuilder = builder;
		return this;
	}

	/**
	 * Enables Addressing strategy using the provided
	 * {@link SenderPhoneNumberTranslatorBuilder}. It decorates the SMS sender
	 * with a {@link PhoneNumberTranslatorSender}
	 * 
	 * @param builder
	 *            the builder to use to build the {@link PhoneNumberTranslator}
	 *            instead of using the default one
	 * @return this instance for fluent use
	 */
	public SmsBuilder withSenderPhoneNumberTranslation(PhoneNumberTranslatorBuilder builder) {
		senderNumberTranslatorBuilder = builder;
		return this;
	}

	/**
	 * <p>
	 * Get the builder used for filling messages.
	 * </p>
	 * 
	 * Access this builder if you want to:
	 * <ul>
	 * <li>Enable/disable automatic filling of messages with values provided in
	 * configuration</li>
	 * <li>Enable/disable automatic filling of subject for messages based on
	 * templates</li>
	 * <li>Add your own message filler</li>
	 * </ul>
	 * 
	 * @return the builder used for filling messages
	 */
	public MessageFillerBuilder getMessageFillerBuilder() {
		return messageFillerBuilder;
	}

	/**
	 * <p>
	 * Get the builder used transform the content of the message. It may be
	 * useful to fine tune templating mechanism, resource inlining and messages
	 * with with several contents.
	 * </p>
	 * 
	 * Access this builder if you want to:
	 * <ul>
	 * <li>Customize templating mechanism (see {@link #getTemplateBuilder()})</li>
	 * <li>Enable/disable support for messages with multiple contents</li>
	 * <li>Enable/disable support for inlining of resources</li>
	 * <li>Add your own content translator</li>
	 * </ul>
	 * 
	 * @return the builder used to transform the content of the message
	 */
	public ContentTranslatorBuilder getContentTranslatorBuilder() {
		return contentTranslatorBuilder;
	}

	/**
	 * <p>
	 * Shortcut to directly access template builder for fine tuning templating
	 * mechanism.
	 * </p>
	 * 
	 * Access this builder if you want to:
	 * <ul>
	 * <li>Customize how template resources are resolved</li>
	 * <li>Register a custom lookup mapping resolver for template resources</li>
	 * <li>Use your own template engine</li>
	 * <li>Customize the template engine configuration</li>
	 * <li>Set the prefix and suffix for template resolution</li>
	 * <li>Set the property key for prefix and suffix resolution</li>
	 * </ul>
	 * 
	 * @return the template builder
	 */
	public TemplateBuilder getTemplateBuilder() {
		return contentTranslatorBuilder.getTemplateBuilder();
	}

	/**
	 * <p>
	 * Get the builder for transformation of recipient phone numbers.
	 * </p>
	 * 
	 * Access to this builder if you want to:
	 * <ul>
	 * <li>Enable/disable international format transformation</li>
	 * </ul>
	 * 
	 * @return the builder for transformation of recipient phone numbers
	 */
	public PhoneNumberTranslatorBuilder getRecipientNumberTranslatorBuilder() {
		return recipientNumberTranslatorBuilder;
	}

	/**
	 * <p>
	 * Get the builder for transformation of sender phone numbers.
	 * </p>
	 * 
	 * Access to this builder if you want to:
	 * <ul>
	 * <li>Enable/disable alpha-numeric format transformation</li>
	 * <li>Enable/disable short code format transformation</li>
	 * <li>Enable/disable international format transformation</li>
	 * </ul>
	 * 
	 * @return the builder for transformation of sender phone numbers
	 */
	public PhoneNumberTranslatorBuilder getSenderNumberTranslatorBuilder() {
		return senderNumberTranslatorBuilder;
	}
}
