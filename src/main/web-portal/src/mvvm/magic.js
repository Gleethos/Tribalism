/**
 *  The API of the view models we use in the frontend is dynamically generated from
 *  the class information the Java backend sends us through the web socket.
 *  I don't know how to do this kind of runtime magic in TypeScript...
 *  ...so let's just do it in plain old JS.
 *
 * @param target A thing onto which we want to attach a method during runtime.
 * @param name The name of the method we want to attach.
 * @param thing The method (or variable) we want the method to return.
 */
export function attachMagic(target, name, thing) {
    target[name] = thing;
}
