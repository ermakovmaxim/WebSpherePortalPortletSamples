/*
 * (C) Copyright IBM Corp. 2014
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package com.ibm.portal.samples.mail.list.model;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileItemFactory;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.portlet.PortletFileUpload;

import com.ibm.portal.Committable;
import com.ibm.portal.Disposable;
import com.ibm.portal.portlet.service.credentialvault.CredentialVaultException;
import com.ibm.portal.samples.common.Marshaller;

/**
 * Action implementation that reads the action from a multipart form post. Each
 * entry in the form post represents a {@link KEY} and is mapped via the
 * <code>name</code> attribute of the entry. The implementation of the
 * {@link KEY} enumeration decodes the entry into an {@link ActionEntry}
 * instance. In case the key represents an action, the value of the field
 * identifies the {@link ACTION} instance which in turn executes the action
 * based on the {@link ActionEntry} bean.
 * 
 * Action implementations modify the underlying {@link MailListModel} which will
 * be encoded into the navigational state after the end of the action phase. If
 * an action resulted in a persistent change of data, the action implementation
 * should return <code>true</code> to indicate this and the framework will call
 * the {@link MailListActions#commit()} method.
 * 
 * @author cleue
 */
public class MailListActions implements Committable, Disposable {

	/**
	 * The different kind of actions for this portlet. The action values appear
	 * as parameters of the {@link KEY#ACTION} parameter. Implementations make
	 * callbacks into the {@link ActionEntry} bean which in turn assembles the
	 * action data and invokes the action implementation on the
	 * {@link MailListActions} bean.
	 * 
	 * The purposes of this {@link Enum} is to provide a reference to all
	 * available actions such that is can be serialized and deserialized to and
	 * from a form.
	 * 
	 * TODO add custom actions here
	 */
	public enum ACTION {

		/**
		 * Enters the credentials
		 */
		ENTER_CREDENTIALS {
			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * com.ibm.portal.samples.portlettemplate.model.MailListActions.
			 * ACTION
			 * #processAction(com.ibm.portal.samples.portlettemplate.model.
			 * MailListActions.ActionEntry)
			 */
			@Override
			protected boolean processAction(final ActionEntry aEntry)
					throws Exception {
				// sanity check
				assert aEntry != null;
				// dispatch
				return aEntry.processEnterCredentials();
			}
		},

		/**
		 * Undefined action as a fallback
		 */
		UNDEFINED {
			@Override
			protected boolean processAction(ActionEntry aEntry)
					throws Exception {
				// sanity check
				assert aEntry != null;
				// dispatch
				return aEntry.processUndefined();
			}

		};

		/**
		 * Executes the action on the model
		 * 
		 * @param aEntry
		 *            the action to execute
		 * @return <code>true</code> if the action modified persistent state,
		 *         else <code>false</code>
		 * 
		 * @throws Exception
		 */
		protected abstract boolean processAction(final ActionEntry aEntry)
				throws Exception;
	}

	/**
	 * Holder for the current action processing. This bean gets filled during
	 * the action decoding process.
	 */
	private final class ActionEntry {

		/**
		 * Character set used to decode the form entries. We start with UTF-8
		 * and then update the characterset depending on the existence of a
		 * "_charset_" field. This field name is a de-facto standard in
		 * browsers, that fill it in with the actual character set used to
		 * encode the parameters.
		 */
		private String charset = "UTF-8";

		/**
		 * current form data entry
		 */
		private FileItem currentEntry;

		/**
		 * Iterator over the existing form data entries
		 */
		private final Iterator<FileItem> itemIterator;

		/**
		 * currently decoded password
		 */
		private String password;

		/**
		 * currently decoded username
		 */
		private String username;

		/**
		 * Initialize the action entry by decoding the request content
		 * 
		 * @throws FileUploadException
		 * @throws IOException
		 */
		private ActionEntry() throws FileUploadException, IOException {
			/**
			 * Iterator over the entries
			 */
			itemIterator = getFileItems().iterator();
		}

		/**
		 * TODO replace by custom action implementation
		 * 
		 * Implementation of the {@link ACTION#ENTER_CREDENTIALS} action
		 * 
		 * @param aUsername
		 *            the username
		 * @param aPassword
		 *            the password
		 * 
		 * @return <code>true</code> if the backend changed after the action,
		 *         else <code>false</code>
		 * @throws CredentialVaultException
		 */
		private final boolean actionEnterCredentials(final String aUsername,
				final String aPassword) throws CredentialVaultException {
			// logging support
			final String LOG_METHOD = "actionEnterCredentials(aUsername, aPassword)";
			if (bIsLogging) {
				LOGGER.entering(LOG_CLASS, LOG_METHOD, new Object[] {
						aUsername, aPassword });
			}
			// save
			final boolean bResult = bean.storeCredentials(aUsername, aPassword);
			// exit trace
			if (bIsLogging) {
				LOGGER.exiting(LOG_CLASS, LOG_METHOD, bResult);
			}
			// nothing changed
			return bResult;
		}

		/**
		 * Decodes the current entry as a key
		 * 
		 * @return the key or <code>null</code> if the key could not be decoded
		 * @throws IOException
		 */
		private final KEY getKey() throws IOException {
			// dispatch to the key decoder
			return privateMarshaller.unmarshalEnum(currentEntry.getFieldName(),
					KEYS, KEY.UNKNOWN);
		}

		/**
		 * Moves to the next entries
		 * 
		 * @return <code>true</code> if we found a next entry, else
		 *         <code>false</code>
		 * 
		 * @throws IOException
		 * @throws FileUploadException
		 */
		private final boolean nextEntry() throws IOException,
				FileUploadException {
			// logging support
			final String LOG_METHOD = "nextEntry()";
			if (bIsLogging) {
				LOGGER.entering(LOG_CLASS, LOG_METHOD);
			}
			// delete the previous entry
			if (currentEntry != null) {
				// remove the entry
				currentEntry.delete();
				currentEntry = null;
			}
			// moves to the next entry
			while (itemIterator.hasNext()) {
				// next field
				currentEntry = itemIterator.next();
				assert currentEntry != null;
				// check for special names
				final String name = currentEntry.getFieldName();
				assert name != null;
				// log this
				if (bIsLogging) {
					LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
							"Field name [{0}].", name);
				}
				// check for special fields
				if (KEY_CHARSET.equals(name)) {
					// update the charset
					charset = readString();
				} else {
					// bail out
					break;
				}
				// reset
				currentEntry.delete();
				currentEntry = null;
			}
			// check if we have an entry
			final boolean bResult = currentEntry != null;
			// exit trace
			if (bIsLogging) {
				LOGGER.exiting(LOG_CLASS, LOG_METHOD, bResult);
			}
			// ok
			return bResult;
		}

		/**
		 * Enters the credentials
		 * 
		 * @return <code>true</code> if the config could be saved, else
		 *         <code>false</code>
		 * @throws CredentialVaultException
		 * 
		 * @see ACTION#ENTER_CREDENTIALS
		 */
		private final boolean processEnterCredentials()
				throws CredentialVaultException {
			// dispatch
			return actionEnterCredentials(username, password);
		}

		/**
		 * Fallback for the undefined action
		 * 
		 * @return <code>false</code>
		 */
		private final boolean processUndefined() {
			return false;
		}

		/**
		 * Decodes the current entry as an action
		 * 
		 * @return the decoded action or <code>null</code> if the action could
		 *         not be decoded
		 * @throws IOException
		 */
		private final ACTION readAction() throws IOException {
			// decodes the action parameter
			return privateMarshaller.unmarshalEnum(readString(), ACTIONS,
					ACTION.UNDEFINED);
		}

		/**
		 * Reads the password
		 * 
		 * @throws IOException
		 * 
		 * @see KEY#USERNAME
		 */
		private final boolean readPassword() throws IOException {
			// reads the text
			password = readString();
			// no persistent modification
			return false;
		}

		/**
		 * Reads a trimmed string
		 * 
		 * @return the string
		 * @throws IOException
		 */
		private final String readString() throws IOException {
			// decodes the stream
			return currentEntry.getString(charset);
		}

		/**
		 * Reads the username
		 * 
		 * @throws IOException
		 * 
		 * @see KEY#USERNAME
		 */
		private final boolean readUsername() throws IOException {
			// reads the text
			username = readString();
			// no persistent modification
			return false;
		}

		/**
		 * Reinitialize the bean for the next action
		 */
		private final void reset() {
			// reset
			username = null;
			password = null;
		}
	}

	/**
	 * Representation to dependencies on external services
	 */
	public interface Dependencies {

		/**
		 * Marshaller for private render parameters.
		 * 
		 * @return the marshaller
		 */
		Marshaller getPrivateParameterMarshaller();

		/**
		 * TODO add dependencies via parameterless getter methods
		 */
	}

	/**
	 * Enumeration over the possible keys that can appear as parameters of an
	 * action request. The key identifiers are transported as the name attribute
	 * of the form input fields of the action form.
	 * 
	 * TODO add entries to the enumeration that represent custom form input
	 */
	public enum KEY {

		/**
		 * Identifies an action. This should be called after the action data has
		 * been processed.
		 */
		ACTION {

			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * com.ibm.portal.samples.portlettemplate.model.MailListActions.
			 * KEY#decodeKey
			 * (com.ibm.portal.samples.portlettemplate.model.MailListActions
			 * .ActionEntry)
			 */
			@Override
			protected boolean decodeKey(final ActionEntry aEntry)
					throws Exception {
				// logging support
				final String LOG_METHOD = "decodeKey(aEntry)";
				final boolean bIsLogging = LOGGER.isLoggable(LOG_LEVEL);
				if (bIsLogging) {
					LOGGER.entering(LOG_CLASS, LOG_METHOD);
				}
				// decode the action key
				final ACTION action = aEntry.readAction();
				// log this
				if (bIsLogging) {
					LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
							"Processing action [{0}].", action);
				}
				// execute the action if we were able to decode it
				final boolean bResult = (action != null) ? action
						.processAction(aEntry) : false;
				// reset the entry
				aEntry.reset();
				// exit trace
				if (bIsLogging) {
					LOGGER.exiting(LOG_CLASS, LOG_METHOD, bResult);
				}
				// ok
				return bResult;
			}
		},

		/**
		 * Form input that represents the password of the authentication form
		 */
		PASSWORD {

			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * com.ibm.portal.samples.portlettemplate.model.MailListActions.
			 * KEY#decodeKey
			 * (com.ibm.portal.samples.portlettemplate.model.MailListActions
			 * .ActionEntry)
			 */
			@Override
			protected boolean decodeKey(final ActionEntry aEntry)
					throws Exception {
				// decode the sample text
				return aEntry.readPassword();
			}

		},

		/**
		 * Unknown key identifier
		 */
		UNKNOWN {

			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * com.ibm.portal.samples.portlettemplate.model.MailListActions.
			 * KEY#decodeKey
			 * (com.ibm.portal.samples.portlettemplate.model.MailListActions
			 * .ActionEntry)
			 */
			@Override
			protected boolean decodeKey(final ActionEntry aEntry)
					throws Exception {
				// TODO you might want to throw an exception in this case
				// nothing special to do
				return false;
			}

		},

		/**
		 * Form input that represents the username of the authentication form
		 */
		USERNAME {

			/*
			 * (non-Javadoc)
			 * 
			 * @see
			 * com.ibm.portal.samples.portlettemplate.model.MailListActions.
			 * KEY#decodeKey
			 * (com.ibm.portal.samples.portlettemplate.model.MailListActions
			 * .ActionEntry)
			 */
			@Override
			protected boolean decodeKey(final ActionEntry aEntry)
					throws Exception {
				// decode the sample text
				return aEntry.readUsername();
			}

		};

		/**
		 * Decodes the key
		 * 
		 * @param aEntry
		 *            the action to execute
		 * @return <code>true</code> if the action modified persistent state,
		 *         else <code>false</code>
		 * @throws Exception
		 */
		protected abstract boolean decodeKey(final ActionEntry aEntry)
				throws Exception;
	}

	/**
	 * Available actions, we maintain a reference to the array, because the
	 * {@link ACTION#values()} method will create a new copy of the array with
	 * each invocation.
	 */
	private static final ACTION[] ACTIONS = ACTION.values();

	/**
	 * name of the hidden charset field
	 */
	private static final String KEY_CHARSET = "_charset_";

	/**
	 * Available keys, we maintain a reference to the array, because the
	 * {@link KEY#values()} method will create a new copy of the array with each
	 * invocation.
	 */
	private static final KEY[] KEYS = KEY.values();

	/** class name for the logger */
	private static final String LOG_CLASS = MailListActions.class.getName();

	/** logging level */
	private static final Level LOG_LEVEL = Level.FINER;

	/** class logger */
	private static final Logger LOGGER = Logger.getLogger(LOG_CLASS);

	/**
	 * Computes the logical or of the entries
	 * 
	 * @param bLeft
	 *            the left flag
	 * @param bRight
	 *            the right flag
	 * @return the result
	 */
	private static final boolean or(final boolean bLeft, final boolean bRight) {
		return bLeft || bRight;
	}

	/**
	 * mail bean
	 */
	private final MailListBean bean;

	/**
	 * logging can be an instance variable, since the lifecycle of the model is
	 * the request
	 */
	private final boolean bIsLogging = LOGGER.isLoggable(LOG_LEVEL);

	/**
	 * List of form data entries
	 */
	private List<FileItem> fileItems;

	/**
	 * access to the APIs that allow to decode the form upload
	 */
	private PortletFileUpload portletFileUpload;

	/**
	 * controls how private parameters are marshalled
	 */
	private final Marshaller privateMarshaller;

	/**
	 * current action request
	 */
	private final ActionRequest request;

	/**
	 * Initializes the model from a portlet request
	 * 
	 * @param aModel
	 *            basic model of the request
	 * @param aRequest
	 *            the request
	 * @param aResponse
	 *            the response
	 * @param aConfig
	 *            the config
	 * @param aDeps
	 *            the dependencies
	 */
	public MailListActions(final MailListModel aModel,
			final MailListBean aBean, final ActionRequest aRequest,
			final ActionResponse aResponse, final Dependencies aDeps) {
		// sanity check
		assert aBean != null;
		assert aModel != null;
		assert aRequest != null;
		assert aResponse != null;
		assert aDeps != null;
		// logging support
		final String LOG_METHOD = "MailListActions(aModel, aBean, aRequest, aResponse, aDeps)";
		if (bIsLogging) {
			LOGGER.entering(LOG_CLASS, LOG_METHOD);
		}
		// TODO copy dependencies from the interface into fields
		bean = aBean;
		request = aRequest;
		privateMarshaller = aDeps.getPrivateParameterMarshaller();
		// exit trace
		if (bIsLogging) {
			LOGGER.exiting(LOG_CLASS, LOG_METHOD);
		}
	}

	/**
	 * Called if all actions have been executed successfully and resulted in
	 * persistent modifications. In this case these modifications have to be
	 * saved to the underlying data store.
	 */
	@Override
	public void commit() {
		// logging support
		final String LOG_METHOD = "commit()";
		if (bIsLogging) {
			LOGGER.entering(LOG_CLASS, LOG_METHOD);
		}
		/**
		 * Executed after the action phase if the action implementation
		 * indicates that backend state has been changed as a side effect of the
		 * action.
		 */
		/**
		 * TODO implement your commit operation here
		 */
		// exit trace
		if (bIsLogging) {
			LOGGER.exiting(LOG_CLASS, LOG_METHOD);
		}
	}

	/**
	 * Performs cleanup of the model resources at the end of the request
	 */
	@Override
	public void dispose() {
		// logging support
		final String LOG_METHOD = "dispose()";
		if (bIsLogging) {
			LOGGER.entering(LOG_CLASS, LOG_METHOD);
		}
		// reset the data
		portletFileUpload = null;
		fileItems = null;
		// exit trace
		if (bIsLogging) {
			LOGGER.exiting(LOG_CLASS, LOG_METHOD);
		}
	}

	/**
	 * Returns the list of items from the multipart request
	 * 
	 * @return the list of items
	 * 
	 * @throws FileUploadException
	 * @throws IOException
	 */
	private final List<FileItem> getFileItems() throws FileUploadException,
			IOException {
		// logging support
		final String LOG_METHOD = "getFileItems()";
		if (fileItems == null) {
			// decode the list
			fileItems = getPortletFileUpload().parseRequest(request);
			// log this
			if (bIsLogging) {
				LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
						"Decoding the file items [{0}].", fileItems);
			}
		}
		// returns the items
		return fileItems;
	}

	/**
	 * Returns access to the form data APIs
	 * 
	 * @return the the APIs
	 * @throws IOException
	 */
	private final PortletFileUpload getPortletFileUpload() throws IOException {
		// logging support
		final String LOG_METHOD = "getPortletFileUpload()";
		// access the data stream
		if (portletFileUpload == null) {
			// file handling
			final FileItemFactory itemFactory = new DiskFileItemFactory();
			// fetch the stream
			portletFileUpload = new PortletFileUpload(itemFactory);
			// log this
			if (bIsLogging) {
				LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
						"Accessing the input stream ...");
			}
		}
		// ok
		return portletFileUpload;
	}

	/**
	 * Execute the actions. This is the main entry point that is called from
	 * {@link TemplatePortlet#doDispatch(ActionRequest, ActionResponse)}. The
	 * implementation interprets the form input stream sequentially. For each
	 * input it looks up the {@link KEY} based on the name of the input field
	 * and executes the registered callback per key. If the key represents data
	 * input, the data is assembled in the {@link ActionEntry}. If the key
	 * represents an action, the action is executed for data assembled so far.
	 * 
	 * @return <code>true</code> if the action resulted in a persistent
	 *         modification, else <code>false</code>
	 * 
	 * @throws Exception
	 */
	public boolean processActions() throws Exception {
		// logging support
		final String LOG_METHOD = "processActions()";
		if (bIsLogging) {
			LOGGER.entering(LOG_CLASS, LOG_METHOD);
		}
		// check if the action has been executed
		boolean bResult = false;
		// decode the action
		final ActionEntry actionEntry = new ActionEntry();
		// process each action entry
		while (actionEntry.nextEntry()) {
			// decode the key
			final KEY key = actionEntry.getKey();
			// log this
			if (bIsLogging) {
				LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
						"Decoding key [{0}].", key);
			}
			// the event loop
			if (key != null) {
				// handle the key and aggregate the result
				bResult = or(key.decodeKey(actionEntry), bResult);
			} else {
				// log this
				if (bIsLogging) {
					LOGGER.logp(LOG_LEVEL, LOG_CLASS, LOG_METHOD,
							"Ignoring the current entry, because the key could not be decoded.");
				}
			}
		}
		// exit trace
		if (bIsLogging) {
			LOGGER.exiting(LOG_CLASS, LOG_METHOD, bResult);
		}
		// ok
		return bResult;
	}
}
