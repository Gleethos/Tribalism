import React from 'react';
import useProperty from '../hooks/useProperty.js';
import Input from '../components/atoms/Input.js';
import Button from '../components/atoms/Button.js';
import { GiShamblingZombie } from 'react-icons/gi';

function LoginView({ vm }) {
  const [username, setUsername] = useProperty(vm, (vm) => vm.username(), '');
  const [password, setPassword] = useProperty(vm, (vm) => vm.password(), '');
  const [feedback] = useProperty(vm, (vm) => vm.feedback(), '');
  const [feedbackColor] = useProperty(vm, (vm) => vm.inputValid(), 'black');
  return (
    <div className=' bg-no-repeat bg-cover bg-black bg-center bg-survival-1 h-[100vh] flex items-center justify-center flex-col gap-6'>
      <h1 className='text-2xl p-2 font-bold rounded-lg hidden'>
        Login Survivor!
      </h1>
      <form
        className='flex justify-center items-center flex-col
      bg-gray-300/30 rounded-lg pt-8 pb-4 gap-6 px-10 w-full max-w-xl'
      >
        <div className='login__username'>
          <Input
            inputStyles={'opacity-50 hover:opacity-100'}
            labelStyles={'font-bold text-lg text-gray-800'}
            labelOnTop={true}
            type={'text'}
            placeholder='Username'
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
        </div>
        <div className='login__password'>
          <Input
            inputStyles={'opacity-50 hover:opacity-100'}
            labelStyles={'font-bold text-lg text-gray-800'}
            labelOnTop={true}
            type={'password'}
            placeholder='Password'
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
        </div>
        <div>
          <Button
            buttonStyles='flex gap-3'
            kind='primary'
            onClick={() => vm.login()}
          >
            <span className='block'>Login</span>
            <GiShamblingZombie className='text-2xl' />
          </Button>
        </div>

        <div>
          <span className='block pt-3'>{feedback}</span>
        </div>
      </form>
    </div>
  );
}

export default LoginView;
