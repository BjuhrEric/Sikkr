package main.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import main.model.Contact;
import main.model.Message;

public final class FileUtility {


	private final static XMLInputFactory INPUT_FACTORY = XMLInputFactory.newFactory();
	private final static XMLOutputFactory OUTPUT_FACTORY = XMLOutputFactory.newFactory();
	
	private FileUtility() {
		throw new UnsupportedOperationException("Cannot create instance of this class");
	}
	
	public static List<Contact> readContacts() throws Exception {
		String filename = "contacts.xml";
		Logger.getGlobal().info("Reading contacts from: "+(new File(filename)).getAbsolutePath());
		List<Contact> contacts = new ArrayList<>();
		if ((new File(filename)).exists()) {
			XMLStreamReader reader = INPUT_FACTORY.createXMLStreamReader(filename, new FileInputStream(filename));
			while (reader.hasNext()) {
				if (reader.next() == XMLStreamReader.START_ELEMENT 
						&& reader.getName().getLocalPart().equals("Contact")) {
					String number = reader.getAttributeValue(0); //Number
					RSAPublicKey key = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(getPublicKeyFromFile("contacts/" + number + ".key")));
                    Contact contact = new Contact(number, key);
					contacts.add(contact);
				}
			}
		}
		return contacts;
	}
	
	public static List<Message> readMessages() throws Exception {
		String filename = "messages.xml";
		List<Message> messages = new ArrayList<>();
		if ((new File(filename)).exists()) {
			XMLStreamReader reader = INPUT_FACTORY.createXMLStreamReader(filename, new FileInputStream(filename));
			while (reader.hasNext()) {
				if (reader.next() == XMLStreamReader.START_ELEMENT 
						&& reader.getName().getLocalPart().equals("Message")) {
					String sender = reader.getAttributeValue(0); //Sender
					String reciever = reader.getAttributeValue(1); //Reciever
					long time = Long.parseLong(reader.getAttributeValue(2)); //Timestamp
					String path = reader.getAttributeValue(3); //Content path
					messages.add(new Message(getMessageContent(path), sender, reciever, time));
				}
			}
		}
		return messages;
	}
	
	private static byte[] getMessageContent(String path) throws Exception {
		DataInputStream dis = new DataInputStream(new FileInputStream("messages/"+path+".msg"));
		byte[] read = new byte[dis.available()];
		dis.readFully(read);
		dis.close();
		return read;
	}
	
	public static void saveMessages(Collection<Contact> contacts) throws Exception {
		String messagesFilename = "messages.xml";
		File messagesFile = new File(messagesFilename);
		XMLStreamWriter writer;
		Set<Message> messages = new HashSet<>();
		
		if (messagesFile.exists() && !messagesFile.delete()) {
			throw new IOException("Could not remove old file");
		}

        if (!messagesFile.exists() && !messagesFile.createNewFile()) {
            throw new IOException("Could not create a new messages file");
        }
		writer = OUTPUT_FACTORY.createXMLStreamWriter(new FileOutputStream(messagesFile));
		writer.writeStartDocument();
		writer.writeStartElement("Messages");
		
		contacts.forEach((contact) -> addAll(messages, contact.sentMessages, contact.recievedMessages));
		messages.forEach((msg) -> saveMessage(writer, msg));
		
		writer.writeEndDocument();
		writer.flush();
		writer.close();
	}
	
	private static void saveMessage(XMLStreamWriter writer, Message message) {
		try {
			File contentDir = new File("messages/");
			File contentFile;
			String path;
			writer.writeEmptyElement("Message");
			writer.writeAttribute("sender", message.getSender());
			writer.writeAttribute("reciever", message.getReciever());
			writer.writeAttribute("time", message.getTimeInMillis() + "");
			writer.writeAttribute("content", path = getRandomContentPath(message));
			
			if (!contentDir.exists() && !contentDir.mkdir()) {
				throw new IOException("Could not create non existing content dir");
			}
			
			contentFile = new File(contentDir, path + ".msg");
			DataOutputStream dos = new DataOutputStream(new FileOutputStream(contentFile));
			dos.write(message.getContent());
			dos.flush();
			dos.close();
		} catch (XMLStreamException | IOException e) {
			e.printStackTrace();
		}
	}
	
	private static String getRandomContentPath(Message message) {
		Random random = new Random();
		String path;
		File dir = new File("messages/");
		File tmp;
		do {
			random.setSeed(random.nextLong() * message.hashCode());
			path = ""+random.nextInt();
			tmp = new File(dir, path);
		} while (tmp.exists());
		return path;
	}
	
	
	@SafeVarargs
	private static <T> void addAll(Collection<? super T> addTo, Collection<? extends T>... from) {
		for (Collection<? extends T> col : from) {
			addTo.addAll(col);
		}
	}
	
	public static void saveContacts(Collection<Contact> contactList) throws Exception {
		File contacts = new File("contacts.xml");
		if (contacts.exists() && !contacts.delete()) {
			throw new IOException("Could not remove old contacts.xml file");
		}

        if (!contacts.exists() && !contacts.createNewFile()) {
            throw new IOException("Could not create non existing contacts.xml file");
        }

		XMLStreamWriter writer = OUTPUT_FACTORY.createXMLStreamWriter(new FileOutputStream(contacts));
		writer.writeStartDocument();
		writer.writeStartElement("ContactBook");
		contactList.forEach((contact) -> writeContact(writer, contact));
		writer.writeEndDocument();
		writer.flush();
		writer.close();
	}
	
	private static void writeContact(XMLStreamWriter writer, Contact contact) {
		try {
			File keyDirectory = new File("contacts/");
			File keyFile = new File(keyDirectory, contact.getNumber()+".key");
			writer.writeEmptyElement("Contact");
			writer.writeAttribute("number", contact.getNumber());
			
			if (keyFile.exists() && !keyFile.delete()) {
				throw new IOException("Could not delete old key file");
			}
			
			if (!keyDirectory.exists() && !keyDirectory.mkdir()) {
				throw new IOException("Could not create non existing key file directory");
			}
			
			if (!keyFile.exists() && !keyFile.createNewFile()) {
                throw new IOException("Could not create new key file");
            }
			
			DataOutputStream dos = new DataOutputStream(new FileOutputStream(keyFile));
			dos.write(contact.getPublicKey());
			dos.flush();
			dos.close();
		} catch (XMLStreamException | IOException e) {
			e.printStackTrace();
		}
	}
	
	private static byte[] getPublicKeyFromFile(String file) throws IOException {
		DataInputStream bis = new DataInputStream(new FileInputStream(file));
		byte[] read = new byte[bis.available()];
		bis.readFully(read);
		bis.close();
		return read;
	}
	
}
