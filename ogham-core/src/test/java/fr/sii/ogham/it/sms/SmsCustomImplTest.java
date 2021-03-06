package fr.sii.ogham.it.sms;

import java.io.IOException;
import java.util.Properties;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import fr.sii.ogham.core.builder.MessagingBuilder;
import fr.sii.ogham.core.condition.FixedCondition;
import fr.sii.ogham.core.exception.MessagingException;
import fr.sii.ogham.core.message.Message;
import fr.sii.ogham.core.sender.MessageSender;
import fr.sii.ogham.core.service.MessagingService;
import fr.sii.ogham.helper.rule.LoggingTestRule;
import fr.sii.ogham.sms.message.Sender;
import fr.sii.ogham.sms.message.Sms;
import fr.sii.ogham.sms.message.addressing.AddressedPhoneNumber;
import fr.sii.ogham.sms.message.addressing.NumberingPlanIndicator;
import fr.sii.ogham.sms.message.addressing.TypeOfNumber;

@RunWith(MockitoJUnitRunner.class)
public class SmsCustomImplTest {
	private static final String NATIONAL_PHONE_NUMBER = "0203040506";

	private static final String INTERNATIONAL_PHONE_NUMBER = "+33203040506";

	private MessagingService oghamService;

	@Rule
	public final LoggingTestRule loggingRule = new LoggingTestRule();
	
	@Mock
	MessageSender customSender;
	
	@Before
	public void setUp() throws IOException {
		Properties props = new Properties(System.getProperties());
		props.load(getClass().getResourceAsStream("/application.properties"));
		MessagingBuilder builder = new MessagingBuilder().useAllDefaults(props);
		builder.getSmsBuilder().registerImplementation(new FixedCondition<Message>(true), customSender);
		oghamService = builder.build();
	}
	
	@Test
	public void simple() throws MessagingException {
		oghamService.send(new Sms("sms content", NATIONAL_PHONE_NUMBER));
		Mockito.verify(customSender).send(new Sms("sms content",
				new Sender(new AddressedPhoneNumber(INTERNATIONAL_PHONE_NUMBER, TypeOfNumber.INTERNATIONAL, NumberingPlanIndicator.ISDN_TELEPHONE)),
				new AddressedPhoneNumber(NATIONAL_PHONE_NUMBER, TypeOfNumber.UNKNOWN, NumberingPlanIndicator.ISDN_TELEPHONE)));
	}
}
