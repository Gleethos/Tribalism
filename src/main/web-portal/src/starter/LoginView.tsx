import React from 'react';
import useProperty from '../hooks/useProperty';
import Input from '../components/atoms/Input';
import Button from '../components/atoms/Button';
import { GiShamblingZombie, GiRaiseZombie } from 'react-icons/gi';

function LoginView({ vm }: any) {
  const [username, setUsername] = useProperty(vm, (vm) => vm.username(), '');
  const [password, setPassword] = useProperty(vm, (vm) => vm.password(), '');
  const [feedback] = useProperty(vm, (vm) => vm.feedback(), '');
  const [feedbackColor] = useProperty(vm, (vm) => vm.feedbackColor(), 'black');
  const [usernameBackgroundColor] = useProperty(
    vm,
    (vm) => vm.usernameBackgroundColor(),
    'white',
  );
  const [passwordBackgroundColor] = useProperty(
    vm,
    (vm) => vm.passwordBackgroundColor(),
    'white',
  );
  return (
    <div className=' bg-no-repeat bg-cover bg-black bg-center bg-survival-1 h-[100vh] flex items-center justify-center flex-col gap-6'>
      <form
        className='flex justify-center items-center flex-col
      bg-gray-300/30 rounded-lg pt-6 pb-4 gap-6 px-10 w-full max-w-xl'
      >
        <h1 className='text-2xl text-gray-800 p-2 font-bold  border-b-2 border-slate-300 pb-4 mb-4'>
          Login Survivor!
        </h1>
        <div className='login__username'>
          <Input
            inputStyles={'opacity-50 hover:opacity-100'}
            labelStyles={'font-bold text-lg text-gray-800'}
            labelOnTop={true}
            type={'text'}
            placeholder='Username'
            value={username as string}
            onChange={(event) => setUsername(event.target.value as string)}
          />
        </div>
        <div className='login__password'>
          <Input
            inputStyles={'opacity-50 hover:opacity-100'}
            labelStyles={'font-bold text-lg text-gray-800'}
            labelOnTop={true}
            type={'password'}
            placeholder='Password'
            value={password as string}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        <div className='flex flex-col gap-4'>
          <Button
            buttonStyles='flex gap-3 items-center justify-center'
            kind='primary'
            onClick={() => vm.login()}
          >
            <span className='block'>Login</span>
            <GiShamblingZombie className='text-2xl' />
          </Button>
          <span>or</span>
          <Button
            buttonStyles='flex gap-3 scale-[0.9] !bg-gray-500/80 hover:!scale-[1]'
            kind='secondary'
          >
            <span className='block' onClick={() => vm.switchToRegister()}>
              Register
            </span>
            <GiRaiseZombie className='text-2xl' />
          </Button>
        </div>

        <div>
          <span className={`block pt-3 text-${feedbackColor}-900`}>
            {feedback as React.ReactNode}
          </span>
        </div>
      </form>
    </div>
  );
}

export default LoginView;
