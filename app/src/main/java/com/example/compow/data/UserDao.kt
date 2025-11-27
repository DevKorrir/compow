package com.example.compow.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId")
    fun getUserByIdFlow(userId: String): Flow<UserEntity?>

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getCurrentUser(): UserEntity?

    @Query("SELECT * FROM users LIMIT 1")
    fun getCurrentUserFlow(): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT * FROM users WHERE phone_number = :phoneNumber LIMIT 1")
    suspend fun getUserByPhoneNumber(phoneNumber: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity): Long

    @Update
    suspend fun updateUser(user: UserEntity)

    @Query("UPDATE users SET full_name = :fullName WHERE userId = :userId")
    suspend fun updateUserName(userId: String, fullName: String)

    @Query("UPDATE users SET profile_picture_uri = :uri WHERE userId = :userId")
    suspend fun updateProfilePicture(userId: String, uri: String?)

    @Query("UPDATE users SET email = :email WHERE userId = :userId")
    suspend fun updateEmail(userId: String, email: String)

    @Query("UPDATE users SET phone_number = :phoneNumber WHERE userId = :userId")
    suspend fun updatePhoneNumber(userId: String, phoneNumber: String)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE email = :email)")
    suspend fun isEmailExists(email: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE phone_number = :phoneNumber)")
    suspend fun isPhoneNumberExists(phoneNumber: String): Boolean

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int
}