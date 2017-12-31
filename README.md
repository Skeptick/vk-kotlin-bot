VK Kotlin Bot
=============
Библиотека с DSL-подобным синтаксисом на языке Kotlin для создания чат-бота для vk.com.

На данный момент поддерживается обработка следующих событий от LongPolling сервера:
  - Сообщения из чата (беседы) / от пользователя / от сообщества
  - Создание чата
  - Смена заголовка чата
  - Изменение / удаление фото чата
  - Приглашение / исключение пользователя в / из чата

Реализованы некоторые базовые методы для работы с API Вконтакте, такие как: отправка сообщений, получение сообщений по ID, получение списка пользователей в чате и т.д.

Пример использования
--------------------
```kotlin
fun main(args: Array<String>) = runBlocking {
  val bot = BotApplication("YOUR_ACCESS_TOKEN")
  bot.anyMessage { main() }
  bot.onChatPhotoRemove { /* ... */ }
  bot.onChatKickUser { /* ... */ }
  bot.run()
}

fun DefaultMessageRoute.main() {

  onMessageFrom<Chat> {
    // сюда пройдут только сообщения из чата
    // помимо <Chat> доступны параметры User и Community
        
    onIncomingMessage {
      // сюда пройдут только входящие сообщения из чата
            
      onMessage("привет") {
        // только входящие сообщения из чата 
        // начинающиеся с "привет"
                
        handle { 
          // код в этом блоке отработает в любом случае
        }
                
        onMessage("бот", "робот") { 
          // ...
        }
                
        onMessage("как дела") {
          // ...
        }
                
        intercept { 
          // код в этом блоке отработает, 
          // только если событие не удовлетворяет 
          // ни одному другому маршруту.
          
          it.message.respondWithForward("Неизвестная команда!")
        }
        
      }
      
    }
    
  }
  
}
```
[Реальный бот], написанный с использованием библиотеки (работает только в чатах).
Его реализация находится в директории `/app`.

License
=======

    Copyright 2017 Danil Yudov

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

   [Реальный бот]: <https://vk.com/bethoven.olegovich>