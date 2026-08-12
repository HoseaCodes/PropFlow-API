package com.hoseacodes.propflow.model;

/**
 * Coarse-grained account role.
 *
 * <p>Roles answer "what kind of account is this", not "may this account touch
 * this row". Ownership scoping answers the second question, and the two are not
 * interchangeable: without ownership checks, every USER could still read every
 * other user's financial records.
 */
public enum Role {

    /** Standard account. May manage only its own resources. */
    USER,

    /** Administrative account. May manage user accounts. */
    ADMIN
}
