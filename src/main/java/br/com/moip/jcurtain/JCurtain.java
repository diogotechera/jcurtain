package br.com.moip.jcurtain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisException;

import java.net.URI;
import java.util.Random;

public class JCurtain {

    private static final Logger LOGGER = LoggerFactory.getLogger(JCurtain.class);

    private JedisPool jedisPool;

    /**
     * Creates an instance of JCurtain
     *
     * @param jedisPool your pre-configured JedisPool variable
     */
    public JCurtain(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    /**
     * Creates an instance of JCurtain
     *
     * @param uri your redis URI, e.g.: redis://:p4ssw0rd@10.0.1.1:6380/15
     */
    public JCurtain(URI uri) {
        this.jedisPool = new JedisPool(uri);
    }

    /**
     * <p>Checks if your feature is open.
     *
     * <p>You must have a Redis key with name "feature:[name-of-feature]:percentage"
     * with value between 0 and 100.
     *
     * <p>Examples:
     *
     * <ul compact>
     * <li>feature:[name-of-feature]:percentage = 0 (feature not open)
     * <li>feature:[name-of-feature]:percentage = 100 (feature completely open)
     * <li>feature:[name-of-feature]:percentage = 30 (see below)
     * </ul>
     *
     *  <p>If you're in the last case then JCurtain will generate a random number X
     *  between 0 and 100 and will calculate if your defined percentage P is greater
     *  than X. If that condition is true then the feature is open for this method
     *  call. Subsequent method calls can generate different results.
     *
     * @param feature  the feature name
     * @return         <code>true</code> if open
     */
    public boolean isOpen(String feature) {
        try {
            return isFeatureOpen(feature);
        } catch (JedisException e) {
            LOGGER.error("[JCurtain] Redis error. Returning default value FALSE for [feature="+feature+"]", e);
            return false;
        }
    }

    /**
     * <p>Checks if the specified feature is open for the user.
     *
     * <p>This method first checks if the user is member of the {@link java.util.Set} stored at the
     * Redis key "feature:[name-of-feature]:members".
     *
     * <p>If the user is not present in the list and your feature is configured with percentage
     * then this method will behave like {@link JCurtain#isOpen isOpen(feature)}
     *
     * <p>Note that you can open your feature to a restrict set of users by using 0 of percentage.
     *
     * @param feature  the feature name
     * @param user     the unique identifier for the user (email, login, sequential ID etc)
     * @return         <code>true</code> if open
     */
    public boolean isOpen(String feature, String user) {
        try {
            return isOpenForUser(feature, user) || isFeatureOpen(feature);
        } catch (JedisException e) {
            LOGGER.error("[JCurtain] Redis error. Returning default value FALSE for [feature="+feature+"]", e);
            return false;
        }
    }

    /**
     * This method opens a feature for an user
     *
     * @param feature   the feature name
     * @param user     the unique identifier for the user (email, login, sequential ID etc)
     */
    public void openFeatureForUser(String feature, String user) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd("feature:"+feature+":users", user);
        } catch (JedisException e) {
            LOGGER.error("[JCurtain] Redis error. Returning default value FALSE for [user="+user+",feature="+feature+"]", e);
        }
    }


    /**
     * <p>This method returns a {@link Feature} object with the feature configuration and members.
     *
     * @param name the feature name
     * @return the {@link Feature}
     */
    public Feature getFeature(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            return new Feature(name, getFeaturePercentage(name), jedis.smembers("feature:" + name + ":users"));
        } catch (JedisException e) {
            LOGGER.error("[JCurtain] Redis error. Returning default value FALSE for [feature="+e+"]", e);
            return null;
        }
    }

    private boolean isOpenForUser(String feature, String user) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember("feature:" + feature + ":users", user);
        }
    }

    private int getFeaturePercentage(String feature) {
        try (Jedis jedis = jedisPool.getResource()) {
            String featurePercentage = jedis.get("feature:" + feature + ":percentage");
            return (featurePercentage == null ? 0 : Integer.parseInt(featurePercentage));
        }
    }

    private boolean isFeatureOpen(String feature) {
        return randomPercentage() <= getFeaturePercentage(feature);
    }

    private int randomPercentage() {
        return new Random().nextInt(100) + 1;
    }

}