import zio.http.*
import zio.http.endpoint.*
import zio.*
import zio.schema.*
import zio.http.codec.HttpCodec

trait AccountService:
  def createAccount(accountId: String, delay: Option[Int]): ZIO[Any, AccountAlreadyExists, Unit]
  def deposit(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound, Unit]
  def withdraw(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound | InsufficientFunds, Unit]
  def balance(accountId: String, delay: Option[Int]): ZIO[Any, AccountNotFound, Int]

object AccountService:
  val live = ZLayer.derive[AccountServiceImpl]

class AccountServiceImpl(state: Ref.Synchronized[Map[String, Int]]) extends AccountService:

  def createAccount(accountId: String, delay: Option[Int]): ZIO[Any, AccountAlreadyExists, Unit] =
    ZIO.sleep(Duration.fromSeconds(delay.getOrElse(0))) *>
      state.updateZIO { map =>
        if map.contains(accountId) then
          ZIO.logError(s"Account $accountId already exists") *> ZIO.fail(
            AccountAlreadyExists(accountId)
          )
        else ZIO.succeed(map.updated(accountId, 0)) <* ZIO.logInfo(s"Account $accountId created")
      }

  def deposit(accountId: String, amount: Int, delay: Option[Int]): ZIO[Any, AccountNotFound, Unit] =
    ZIO.sleep(Duration.fromSeconds(delay.getOrElse(0))) *>
      state.updateZIO { map =>
        if map.contains(accountId) then
          ZIO.succeed(map.updated(accountId, map(accountId) + amount)) <* ZIO.logInfo(
            s"Deposited $amount to account $accountId"
          )
        else ZIO.logError(s"Account $accountId not found") *> ZIO.fail(AccountNotFound(accountId))
      }

  def withdraw(
      accountId: String,
      amount: Int,
      delay: Option[Int]
  ): ZIO[Any, AccountNotFound | InsufficientFunds, Unit] =
    ZIO.sleep(Duration.fromSeconds(delay.getOrElse(0))) *>
      state.updateZIO { map =>
        if map.contains(accountId) then
          if map(accountId) >= amount then
            ZIO.succeed(map.updated(accountId, map(accountId) - amount)) <* ZIO.logInfo(
              s"Withdrew $amount from account $accountId"
            )
          else
            ZIO.logError(s"Account $accountId has insufficient funds") *> ZIO.fail(
              InsufficientFunds(accountId)
            )
        else
          ZIO.logError(s"Account $accountId has insufficient funds") *> ZIO.fail(
            AccountNotFound(accountId)
          )
      }

  def balance(accountId: String, delay: Option[Int]): ZIO[Any, AccountNotFound, Int] =
    ZIO.sleep(Duration.fromSeconds(delay.getOrElse(0))) *>
      state.get.flatMap(map =>
        if map.contains(accountId) then ZIO.succeed(map(accountId)) <* ZIO.logInfo(s"Balance of account $accountId")
        else ZIO.logError(s"Account $accountId not found") *> ZIO.fail(AccountNotFound(accountId))
      )
