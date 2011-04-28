/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.android.showcase.social.twitter;

import java.util.List;

import org.springframework.android.showcase.R;
import org.springframework.security.crypto.encrypt.AndroidEncryptors;
import org.springframework.social.connect.Connection;
import org.springframework.social.connect.DuplicateConnectionException;
import org.springframework.social.connect.sqlite.SQLiteConnectionRepository;
import org.springframework.social.connect.sqlite.support.SQLiteConnectionRepositoryHelper;
import org.springframework.social.connect.support.ConnectionFactoryRegistry;
import org.springframework.social.oauth1.AuthorizedRequestToken;
import org.springframework.social.oauth1.OAuth1Operations;
import org.springframework.social.oauth1.OAuth1Parameters;
import org.springframework.social.oauth1.OAuthToken;
import org.springframework.social.twitter.api.TwitterApi;
import org.springframework.social.twitter.connect.TwitterConnectionFactory;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

/**
 * @author Roy Clarkson
 */
public class TwitterConnectController 
{
	private static final String TWITTER_PREFERENCES = "TwitterConnectPreferences";
	private static final String REQUEST_TOKEN_KEY = "request_token";
	private static final String REQUEST_TOKEN_SECRET_KEY = "request_token_secret";

	private final Context _context;
	private ConnectionFactoryRegistry _connectionFactoryRegistry;
	private TwitterConnectionFactory _connectionFactory;
	private SQLiteOpenHelper _repositoryHelper;
	private SQLiteConnectionRepository _connectionRepository;

	
	//***************************************
    // Constructors
    //***************************************
	public TwitterConnectController(Context context)
	{
		_context = context;
		_connectionFactoryRegistry = new ConnectionFactoryRegistry();
		_connectionFactory = new TwitterConnectionFactory(getConsumerToken(), getConsumerTokenSecret());
		_connectionFactoryRegistry.addConnectionFactory(_connectionFactory);
		_repositoryHelper = new SQLiteConnectionRepositoryHelper(_context);
		_connectionRepository = new SQLiteConnectionRepository(getLocalUserId(), _repositoryHelper, _connectionFactoryRegistry, AndroidEncryptors.text("password", "5c0744940b5c369b"));
	}
	
	
	//***************************************
    // Accessor methods
    //***************************************
	private String getLocalUserId()
	{
		return _context.getString(R.string.local_user_id);
	}

	private String getProviderId()
	{
		return _context.getString(R.string.twitter_provider_id);
	}
	
	private String getConsumerToken()
	{
		return _context.getString(R.string.twitter_consumer_key);
	}
	
	private String getConsumerTokenSecret()
	{
		return _context.getString(R.string.twitter_consumer_key_secret);
	}
		
	private String getOAuthCallbackUrl()
	{
		return _context.getString(R.string.twitter_oauth_callback_url);
	}

	
	//***************************************
    // Public methods
    //***************************************
	@SuppressWarnings("unchecked")
	public TwitterApi getTwitterApi() 
	{
		List<Connection<?>> connections = _connectionRepository.findConnectionsToProvider(getProviderId());
		Connection<TwitterApi> twitter = (Connection<TwitterApi>) connections.get(0);
		return twitter.getApi();
	}
	
	public boolean isConnected() 
	{
		return !_connectionRepository.findConnectionsToProvider(getProviderId()).isEmpty();
	}
	
	public boolean isCallbackUrl(Uri uri) 
	{
		return (uri != null && Uri.parse(getOAuthCallbackUrl()).getScheme().equals(uri.getScheme()));
	}
	
	public String getTwitterAuthorizeUrl() 
	{ 
		OAuth1Operations oauth = _connectionFactory.getOAuthOperations();
		
		// Fetch a one time use Request Token from Twitter
		OAuthToken requestToken = oauth.fetchRequestToken(getOAuthCallbackUrl(), null);
		
		// save the Request Token to be used later
		SharedPreferences preferences = _context.getSharedPreferences(TWITTER_PREFERENCES, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(REQUEST_TOKEN_KEY, requestToken.getValue());
		editor.putString(REQUEST_TOKEN_SECRET_KEY, requestToken.getSecret());
		editor.commit();
		
		// Generate the Twitter authorization url to be used in the browser or web view
		return oauth.buildAuthorizeUrl(requestToken.getValue(), new OAuth1Parameters(getOAuthCallbackUrl()));
	}
	
	public void updateTwitterAccessToken(String verifier) 
	{
		// Retrieve the Request Token that we saved earlier
		SharedPreferences preferences = _context.getSharedPreferences(TWITTER_PREFERENCES, Context.MODE_PRIVATE);		
		String token = preferences.getString(REQUEST_TOKEN_KEY, null);
		String secret = preferences.getString(REQUEST_TOKEN_SECRET_KEY, null);
		OAuthToken requestToken = new OAuthToken(token, secret);
		
		if (token == null || secret == null) 
		{
			return;
		}

		// Authorize the Request Token
		AuthorizedRequestToken authorizedRequestToken = new AuthorizedRequestToken(requestToken, verifier);
		
		// Exchange the Authorized Request Token for the Access Token
		OAuthToken accessToken = _connectionFactory.getOAuthOperations().exchangeForAccessToken(authorizedRequestToken, null);
		
		// The Request Token is no longer needed, so it can be removed 
		preferences.edit().clear().commit();
		
		// Persist the connection and Access Token to the local SQLite 
		Connection<TwitterApi> connection = _connectionFactory.createConnection(accessToken);
		
		try 
		{
			_connectionRepository.addConnection(connection);
		} 
		catch (DuplicateConnectionException e)
		{
			// connection already exists in repository!
		}
		
	}
	
	public void disconnect()
	{
		_connectionRepository.removeConnectionsToProvider(getProviderId());
	}
}