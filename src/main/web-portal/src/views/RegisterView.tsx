import React from 'react';
import { GiRaiseZombie, GiShamblingZombie } from 'react-icons/gi';
import Button from '../components/atoms/Button';
import Input from '../components/atoms/Input';
import useProperty from '../hooks/useProperty';

export default function RegisterView({ vm }: any) {
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
    <div className=' bg-no-repeat bg-cover xl:bg-contain bg-black bg-center bg-survival-4 h-[100vh] flex items-center justify-center flex-col gap-6'>
      <form
        className='flex justify-center items-center flex-col
      bg-gray-300/30 rounded-lg pt-6 pb-4 gap-6 px-10 w-full max-w-xl'
      >
        <h1 className='text-2xl text-gray-700 p-2 font-bold  border-b-2 border-gray-400 pb-4 mb-4'>
          Register, if you dare ...
        </h1>
        <div className='register__username'>
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
        <div className='register__password'>
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
            buttonStyles='flex gap-3 mt-6 items-center justify-center'
            kind='primary'
            onClick={() => vm.register()}
          >
            <span className='block'>Register</span>
            <GiRaiseZombie className='text-2xl' />
          </Button>
          <span>Already have an account?</span>
          <Button
            buttonStyles='flex gap-3 scale-[0.9] !bg-gray-500/80 hover:!scale-[1]'
            kind='secondary'
          >
            <span className='block' onClick={() => vm.switchToLogin()}>
              Login
            </span>
            <GiShamblingZombie className='text-2xl' />
          </Button>
        </div>
        <div>
          <span
            id={'register-feedback'}
            className={`block pt-3 text-${feedbackColor}-900`}
          >
            {feedback as React.ReactNode}
          </span>
        </div>
      </form>
    </div>
  );
}
